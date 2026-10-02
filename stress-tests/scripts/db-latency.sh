#!/usr/bin/env bash
# Línea base de RTT hacia la base: N consultas `SELECT 1` por una sola conexión cifrada.
# El tiempo por consulta es ~RTT + costo mínimo de MySQL. Se mide (t_N - t_1) / (N - 1) para
# descontar el establecimiento de la conexión y el handshake TLS. No usa mysqlslap: con
# --create-schema borra el esquema al terminar, y aquí hay datos.
#
# Uso: db-latency.sh [N=500] [repeticiones=3]
# Local: corre un cliente mysql desde un contenedor de una sola vez dentro de lt-internal (como la
# API, por red, no por loopback); da la línea base de "RTT casi nulo". En la nube (LT_MODE=direct)
# se corre desde el EC2 de la API hacia DB_HOST, con DB_CA para VERIFY_CA. Ver common.sh.
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

N="${1:-500}"
REPS="${2:-3}"

# Cuerpo que se ejecuta donde está el cliente mysql. Variables: N, REPS, H (host), P (puerto), U, SSL_ARGS.
# shellcheck disable=SC2016  # las variables se expanden en el contenedor, no aquí
BODY='
set -eu
now_ns() { date +%s%N; }
run() { # run <n>: n sentencias SELECT 1 en una sola conexión; imprime ns totales
  s=$(now_ns)
  i=0; while [ "$i" -lt "$1" ]; do echo "SELECT 1;"; i=$((i+1)); done \
    | mysql -h "$H" -P "$P" -u"$U" $SSL_ARGS -N -B "$DBN" > /dev/null
  e=$(now_ns); echo $((e - s))
}
cipher=$(echo "SHOW SESSION STATUS LIKE '"'"'Ssl_cipher'"'"';" | mysql -h "$H" -P "$P" -u"$U" $SSL_ARGS -N -B "$DBN" | cut -f2)
echo "ssl_cipher=${cipher:-ninguno}"
r=1
while [ "$r" -le "$REPS" ]; do
  t1=$(run 1); tn=$(run "$N")
  awk -v n="$N" -v t1="$t1" -v tn="$tn" -v r="$r" "BEGIN { printf \"rep=%d queries=%d total_ms=%.1f rtt_ms_per_query=%.3f\n\", r, n, tn/1e6, (tn-t1)/1e6/(n-1) }"
  r=$((r+1))
done
'

echo "# db-latency: $(date -u +%Y-%m-%dT%H:%M:%SZ) modo=${LT_MODE} N=${N}"
if [ "${LT_MODE}" = "local" ]; then
  lt run --rm --no-deps -T --entrypoint bash \
    -e "MYSQL_PWD=${MYSQL_PASSWORD}" -e "N=${N}" -e "REPS=${REPS}" -e H=mysql -e P=3306 \
    -e "U=${MYSQL_USER}" -e "DBN=${DB_NAME}" -e SSL_ARGS=--ssl-mode=REQUIRED \
    mysql -c "${BODY}"
else
  SSL_ARGS="--ssl-mode=REQUIRED"
  [ -n "${DB_CA:-}" ] && SSL_ARGS="--ssl-mode=VERIFY_CA --ssl-ca=${DB_CA}"
  MYSQL_PWD="${MYSQL_PASSWORD}" N="${N}" REPS="${REPS}" H="${DB_HOST:?DB_HOST es obligatorio en modo direct}" \
    P="${DB_PORT}" U="${MYSQL_USER}" DBN="${DB_NAME}" SSL_ARGS="${SSL_ARGS}" bash -c "${BODY}"
fi
