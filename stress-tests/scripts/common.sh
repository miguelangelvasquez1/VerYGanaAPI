#!/usr/bin/env bash
# Funciones comunes de los scripts de medición. Se carga con `source`, no se ejecuta.
#
# Dos modos de entrada, elegidos con LT_MODE:
#   local  (por defecto)  todo por `docker compose exec/run` sobre la red interna lt-internal.
#   direct                por red directa (fase nube): usa `curl` y `mysql` del host que corre el script.
#
# Variables (con valores de la fase local por defecto; en la nube se pasan por ambiente):
#   PLAN                 archivo de plan en local/plans/ (solo local), por defecto do-1vcpu-1gb
#   METRICS_SCRAPE_TOKEN token de /actuator/prometheus (el de env/loadtest.local.env en local)
#   API_URL              URL base de la API, por defecto http://api:8080 (en direct es obligatoria)
#   DB_HOST DB_PORT DB_NAME DB_CA   destino de la base en modo direct (DB_CA = CA de DO, VERIFY_CA)
#   MYSQL_USER MYSQL_PASSWORD MYSQL_FMT   usuario de los scripts SQL (verygana_monitor y su clave local) y formato
# Nada de lo que imprimen estos scripts incluye correos, teléfonos ni credenciales.

LT_MODE="${LT_MODE:-local}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STRESS_DIR="${ROOT_DIR}/stress-tests"
PLAN="${PLAN:-do-1vcpu-1gb}"
API_URL="${API_URL:-http://api:8080}"
DB_NAME="${DB_NAME:-verygana}"
DB_PORT="${DB_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-verygana_monitor}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-lt-monitor-local}"
# Formato de salida de run_sql: --table (legible) o --batch (TSV con encabezado, para snapshot-delta.sh).
MYSQL_FMT="${MYSQL_FMT:---table}"

# Token de scrapeo: variable, o el de env/loadtest.local.env (valor ficticio, no secreto).
if [ -z "${METRICS_SCRAPE_TOKEN:-}" ] && [ -f "${STRESS_DIR}/env/loadtest.local.env" ]; then
  METRICS_SCRAPE_TOKEN="$(grep -E '^METRICS_SCRAPE_TOKEN=' "${STRESS_DIR}/env/loadtest.local.env" | head -n1 | cut -d= -f2-)"
fi
METRICS_SCRAPE_TOKEN="${METRICS_SCRAPE_TOKEN:-}"

# docker compose del proyecto de la prueba (el $LT de la documentación; ver lt.sh).
lt() {
  PLAN="${PLAN}" "${STRESS_DIR}/scripts/lt.sh" "$@"
}

# GET a la API con el token de scrapeo. Uso: api_get /actuator/prometheus
api_get() {
  local path="$1"
  if [ "${LT_MODE}" = "local" ]; then
    lt exec -T tools curl -s -m 10 -H "Authorization: Bearer ${METRICS_SCRAPE_TOKEN}" "${API_URL}${path}"
  else
    curl -s -m 10 -H "Authorization: Bearer ${METRICS_SCRAPE_TOKEN}" "${API_URL}${path}"
  fi
}

# Corre un archivo .sql de solo lectura. Uso: run_sql archivo.sql [usuario] [clave]
run_sql() {
  local file="$1" user="${2:-${MYSQL_USER}}" pass="${3:-${MYSQL_PASSWORD}}"
  if [ "${LT_MODE}" = "local" ]; then
    lt exec -T -e "MYSQL_PWD=${pass}" mysql mysql -u"${user}" "${MYSQL_FMT}" "${DB_NAME}" < "${file}"
  else
    local -a ssl=(--ssl-mode=REQUIRED)
    [ -n "${DB_CA:-}" ] && ssl=(--ssl-mode=VERIFY_CA "--ssl-ca=${DB_CA}")
    MYSQL_PWD="${pass}" mysql -h "${DB_HOST:?DB_HOST es obligatorio en modo direct}" -P "${DB_PORT}" \
      -u"${user}" "${ssl[@]}" "${MYSQL_FMT}" "${DB_NAME}" < "${file}"
  fi
}
