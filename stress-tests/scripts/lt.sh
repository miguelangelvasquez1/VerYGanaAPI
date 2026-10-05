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

# Par RSA de prueba para firmar los JWT (D-21: la imagen ya no lleva certs/private.pem). Se genera
# aquí, una sola vez, en una carpeta que git ignora (stress-tests/env/keys/), y el compose la monta
# en el contenedor `api` de solo lectura. Es desechable: no es la clave de dev ni de ningún ambiente.
# PKCS#8 es el formato que exige la API (`BEGIN PRIVATE KEY`). Para rotarla, borrar la carpeta y
# recrear el contenedor `api` (`$LT up -d --force-recreate api`).
KEYS_DIR="${ROOT_DIR}/stress-tests/env/keys"
if [ ! -s "${KEYS_DIR}/private.pem" ] || [ ! -s "${KEYS_DIR}/public.pem" ]; then
  command -v openssl >/dev/null || { echo "falta openssl para generar el par de claves de prueba" >&2; exit 2; }
  mkdir -p "${KEYS_DIR}"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "${KEYS_DIR}/private.pem" 2>/dev/null
  openssl pkey -in "${KEYS_DIR}/private.pem" -pubout -out "${KEYS_DIR}/public.pem"
  chmod 644 "${KEYS_DIR}/private.pem" "${KEYS_DIR}/public.pem"
  echo "lt.sh: par de claves RSA de prueba generado en stress-tests/env/keys/ (ignorado por git)" >&2
fi

exec docker compose \
  -f stress-tests/docker-compose.yml -f stress-tests/docker-compose.local.yml \
  --env-file "stress-tests/local/plans/${PLAN}.env" \
  --env-file "stress-tests/local/scenarios/${LT_SCENARIO}.env" "$@"
