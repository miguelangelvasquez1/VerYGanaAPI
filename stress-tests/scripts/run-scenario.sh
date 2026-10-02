#!/usr/bin/env bash
# Corre UN escenario de la fase local con toda la medición y deja los resultados en
# stress-tests/results/<fecha>-<escenario>-<plan>/. Equivale a los pasos 6 de GUIDE-local.md.
#
# Uso: PLAN=do-1vcpu-1gb run-scenario.sh <smoke|A|B>
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
