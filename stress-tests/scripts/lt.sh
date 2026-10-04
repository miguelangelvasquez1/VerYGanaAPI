#!/usr/bin/env bash
# Atajo del `$LT` de la documentación: docker compose del proyecto de la prueba (fase local) con el
# archivo de plan elegido en PLAN (por defecto do-1vcpu-1gb). Funciona igual en bash y en zsh (en zsh
# una variable con el comando entero no se parte en palabras, por eso es un script y no `LT="..."`).
#
#   PLAN=do-2vcpu-4gb stress-tests/scripts/lt.sh up -d
#   stress-tests/scripts/lt.sh run --rm -e SCENARIO=smoke k6 run /scripts/main.js
#
# El reparto de CPU/memoria de la API, k6, wiremock y minio, el pool y Tomcat salen de
# local/scenarios/<LT_SCENARIO>.env (A por defecto; B para la corrida B), que se carga DESPUES
# del plan y por eso manda. smoke usa A. Elegir el escenario ANTES de `up`: los límites del `api`
# se fijan al crear el contenedor.
#   LT_SCENARIO=B PLAN=do-2vcpu-4gb stress-tests/scripts/lt.sh up -d
#
# Las variables exportadas en el shell (p. ej. LOADTEST_SEED_USERS) pisan a las de los archivos de plan.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PLAN="${PLAN:-do-1vcpu-1gb}"
[ -f "${ROOT_DIR}/stress-tests/local/plans/${PLAN}.env" ] || { echo "no existe el plan ${PLAN}" >&2; exit 2; }
LT_SCENARIO="${LT_SCENARIO:-A}"
[ "${LT_SCENARIO}" = "smoke" ] && LT_SCENARIO=A
[ -f "${ROOT_DIR}/stress-tests/local/scenarios/${LT_SCENARIO}.env" ] || { echo "no existe el escenario ${LT_SCENARIO} (A o B)" >&2; exit 2; }
cd "${ROOT_DIR}"
exec docker compose \
  -f stress-tests/docker-compose.yml -f stress-tests/docker-compose.local.yml \
  --env-file "stress-tests/local/plans/${PLAN}.env" \
  --env-file "stress-tests/local/scenarios/${LT_SCENARIO}.env" "$@"
