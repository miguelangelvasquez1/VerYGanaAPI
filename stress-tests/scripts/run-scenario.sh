#!/usr/bin/env bash
# Corre UN escenario de la fase local con toda la medición y deja los resultados en
# stress-tests/results/<fecha>-<escenario>-<plan>/. Equivale a los pasos 6 de GUIDE-local.md.
#
# Uso: PLAN=do-1vcpu-1gb run-scenario.sh <smoke|A|B>
#   El perfil de escenario (local/scenarios/A.env o B.env: API, pool, Tomcat, k6, wiremock, minio) se elige
#   con el mismo argumento (smoke usa A). Los límites del `api` se fijan al crear el contenedor: si se
#   levantó con otro escenario, el script avisa y hay que recrearlo con `LT_SCENARIO=<A|B> lt.sh up -d`.
#   Variables opcionales: DURATION_SCALE (acorta las etapas, solo para ensayar), K6_EXTRA (args de k6),
#   STATS_INTERVAL (s, 5), API_INTERVAL (s, 15). Requiere el ambiente arriba y sembrado (guía, pasos 4-5).
#
# Qué hace, en orden: ANALYZE de las tablas grandes, vaciado del resumen de consultas, `check-seed`,
# RTT de la base, snapshot antes, collect-docker-stats y collect-api-metrics en segundo plano, k6,
# snapshot después y delta, top-digests, table-sizes, tls-check, log de la API de la corrida y check-pii.
# Sale con el código de k6 (si fallan los thresholds = distinto de 0); el de check-pii se imprime aparte.
set -uo pipefail
# shellcheck source=common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

SCENARIO="${1:?uso: run-scenario.sh <smoke|A|B>}"
case "${SCENARIO}" in smoke|A|B) ;; *) echo "escenario inválido: ${SCENARIO} (smoke, A o B)" >&2; exit 2 ;; esac
# Perfil de escenario: lo lee lt.sh (smoke usa el de A).
LT_SCENARIO="${SCENARIO}"; [ "${LT_SCENARIO}" = "smoke" ] && LT_SCENARIO=A
export LT_SCENARIO
STATS_INTERVAL="${STATS_INTERVAL:-5}"
API_INTERVAL="${API_INTERVAL:-15}"
STAMP="$(date +%Y%m%d-%H%M)"
RUN_NAME="${STAMP}-${SCENARIO}-${PLAN}"
OUT="${STRESS_DIR}/results/${RUN_NAME}"
SCRIPTS="${STRESS_DIR}/scripts"
mkdir -p "${OUT}"
log() { echo "[$(date +%H:%M:%S)] $*" >&2; }

# La API tiene que estar arriba.
health="$(api_get /actuator/health || true)"
case "${health}" in *UP*) ;; *) echo "la API no responde UP: levantar el ambiente (guía, paso 4)" >&2; exit 3 ;; esac

cp "${STRESS_DIR}/local/plans/${PLAN}.env" "${OUT}/plan.env"
cp "${STRESS_DIR}/local/scenarios/${LT_SCENARIO}.env" "${OUT}/scenario.env"
# Valores EFECTIVOS: lo que compose resuelve (plan + escenario + variables exportadas en el shell) y lo que
# tienen de verdad los contenedores en marcha. Es lo que se midió; plan.env y scenario.env son solo los archivos.
lt --profile tools config --format json 2>/dev/null | python3 -c '
import json, sys
cfg = json.load(sys.stdin)
for name in ("mysql", "api", "k6", "wiremock", "minio"):
    s = cfg["services"].get(name, {})
    env = s.get("environment", {})
    extra = ""
    if name == "api":
        extra = " DB_POOL_SIZE=%s SERVER_TOMCAT_THREADS_MAX=%s" % (env.get("DB_POOL_SIZE"), env.get("SERVER_TOMCAT_THREADS_MAX"))
    if name == "mysql":
        extra = " " + " ".join(s.get("command", []))
    print("%s cpus=%s mem_limit=%s%s" % (name, s.get("cpus"), s.get("mem_limit"), extra))
' > "${OUT}/effective-config.txt" 2>/dev/null || log "aviso: no se pudo resolver la configuración efectiva"
{
  echo "PLAN=${PLAN} LT_SCENARIO=${LT_SCENARIO}"
  for c in mysql api wiremock minio; do
    id="$(lt ps -q "${c}" 2>/dev/null | head -n1)"
    [ -n "${id}" ] && echo "${c} (en marcha) $(docker inspect -f '{{.HostConfig.NanoCpus}} {{.HostConfig.Memory}}' "${id}" 2>/dev/null | awk '{printf "cpus=%g mem_limit=%s", $1/1000000000, $2}')"
  done
} > "${OUT}/running-limits.txt"
# El `api` en marcha tiene que coincidir con el escenario pedido (si no, la corrida mide otra cosa).
want_api="$(grep '^api ' "${OUT}/effective-config.txt" | sed -E 's/.*cpus=([0-9.]+).*/\1/')"
have_api="$(grep '^api (en marcha)' "${OUT}/running-limits.txt" | sed -E 's/.*cpus=([0-9.]+).*/\1/')"
if [ -n "${want_api}" ] && [ "$(echo "${want_api}" | awk '{print $1+0}')" != "$(echo "${have_api}" | awk '{print $1+0}')" ]; then
  log "AVISO: el api en marcha tiene ${have_api} CPU y el escenario ${LT_SCENARIO} pide ${want_api}: recrearlo con LT_SCENARIO=${LT_SCENARIO} lt.sh up -d"
fi
START_ISO="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "${START_ISO}" > "${OUT}/start-utc.txt"

log "ANALYZE de las tablas grandes y vaciado del resumen de consultas"
MYSQL_USER=verygana_app MYSQL_PASSWORD=lt-app-local "${SCRIPTS}/run-sql.sh" analyze-tables.sql "${OUT}/analyze.txt" 2>/dev/null || log "aviso: analyze-tables falló"
MYSQL_USER=root MYSQL_PASSWORD=lt-root-local "${SCRIPTS}/run-sql.sh" reset-digests.sql 2>/dev/null || log "aviso: reset-digests falló"

log "estado antes: check-seed, RTT, snapshot"
"${SCRIPTS}/run-sql.sh" check-seed.sql "${OUT}/check-seed-before.txt" 2>/dev/null
"${SCRIPTS}/db-latency.sh" 500 3 > "${OUT}/db-latency.txt" 2>&1
"${SCRIPTS}/run-sql.sh" snapshot.sql "${OUT}/snapshot-before.tsv" 2>/dev/null

log "recolectores en segundo plano"
"${SCRIPTS}/collect-docker-stats.sh" "${OUT}/docker-stats.csv" "${STATS_INTERVAL}" > /dev/null 2>&1 &
STATS_PID=$!
"${SCRIPTS}/collect-api-metrics.sh" "${OUT}/api-metrics.csv" "${API_INTERVAL}" 86400 > /dev/null 2>&1 &
API_PID=$!
cleanup() { kill -TERM "${STATS_PID}" "${API_PID}" 2>/dev/null || true; wait "${STATS_PID}" "${API_PID}" 2>/dev/null || true; }
trap cleanup EXIT

log "k6 ${SCENARIO} (plan ${PLAN})"
K6_ARGS=(run --rm -e "SCENARIO=${SCENARIO}" -e "RESULTS_DIR=/results/${RUN_NAME}")
[ -n "${DURATION_SCALE:-}" ] && K6_ARGS+=(-e "DURATION_SCALE=${DURATION_SCALE}")
# shellcheck disable=SC2086
lt "${K6_ARGS[@]}" k6 run --quiet ${K6_EXTRA:-} /scripts/main.js > "${OUT}/k6.out" 2>&1
K6_EXIT=$?
echo "${K6_EXIT}" > "${OUT}/k6-exit-code.txt"

cleanup
trap - EXIT
log "k6 terminó con código ${K6_EXIT}; midiendo el estado de después"
"${SCRIPTS}/collect-api-metrics.sh" "${OUT}/api-metrics.csv" 15 0 > /dev/null 2>&1
"${SCRIPTS}/run-sql.sh" snapshot.sql "${OUT}/snapshot-after.tsv" 2>/dev/null
"${SCRIPTS}/snapshot-delta.sh" "${OUT}/snapshot-before.tsv" "${OUT}/snapshot-after.tsv" > "${OUT}/snapshot-delta.tsv" 2>"${OUT}/snapshot-delta.err" \
  || log "aviso: snapshot-delta falló (¿MySQL se reinició durante la corrida?)"
"${SCRIPTS}/run-sql.sh" top-digests.sql "${OUT}/top-digests.txt" 2>/dev/null
"${SCRIPTS}/run-sql.sh" tls-check.sql "${OUT}/tls-check.txt" 2>/dev/null
"${SCRIPTS}/run-sql.sh" table-sizes.sql "${OUT}/table-sizes.txt" 2>/dev/null
"${SCRIPTS}/run-sql.sh" check-seed.sql "${OUT}/check-seed-after.txt" 2>/dev/null
lt logs --no-color --since "${START_ISO}" api > "${OUT}/api.log" 2>&1

log "check-pii sobre los resultados y el log de la API"
"${SCRIPTS}/check-pii.sh" "${OUT}" > "${OUT}/check-pii.txt" 2>&1
PII_EXIT=$?
echo "${PII_EXIT}" > "${OUT}/check-pii-exit-code.txt"

echo "resultados: ${OUT}" >&2
echo "k6 exit=${K6_EXIT} check-pii exit=${PII_EXIT}" >&2
exit "${K6_EXIT}"
