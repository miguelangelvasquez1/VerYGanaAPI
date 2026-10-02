#!/usr/bin/env bash
# Recolecta de /actuator/prometheus las métricas de la API que pide la prueba y el árbol de diagnóstico
# (pool de Hikari, hilos de Tomcat, CPU/heap de la JVM, contador de simuladores) y las guarda en
# formato largo: ts,metric,labels,value. Las peticiones por endpoint salen del resumen de k6, no de aquí.
#
# Uso: collect-api-metrics.sh <salida.csv> [intervalo_s=15] [duracion_s=0]
#   duracion 0 = una sola lectura (p. ej. la final, para loadtest_stub_calls_total); si no, repite
#   hasta cumplir la duración o hasta recibir Ctrl-C/SIGTERM.
# Entrada: LT_MODE=local (docker compose exec tools) o LT_MODE=direct con API_URL (ver common.sh).
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

OUT="${1:?uso: collect-api-metrics.sh <salida.csv> [intervalo_s] [duracion_s]}"
INTERVAL="${2:-15}"
DURATION="${3:-0}"

# Familias que se conservan. Sin etiquetas de URI ni de usuario: solo las del propio componente.
KEEP='^(hikaricp_connections[a-z_]*|tomcat_threads_[a-z_]+|tomcat_connections_[a-z_]+|process_cpu_usage|system_cpu_usage|system_cpu_count|jvm_memory_used_bytes|jvm_memory_max_bytes|jvm_threads_live_threads|jvm_gc_pause_seconds_(count|sum)|jvm_gc_overhead|process_uptime_seconds|loadtest_stub_calls_total)$'

mkdir -p "$(dirname "${OUT}")"
[ -s "${OUT}" ] || echo "ts,metric,labels,value" > "${OUT}"

scrape() {
  local ts body
  ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  body="$(api_get /actuator/prometheus)" || { echo "aviso: no se pudo leer ${API_URL}" >&2; return 0; }
  if ! grep -q '^# TYPE' <<<"${body}"; then
    echo "aviso: /actuator/prometheus no devolvió métricas (¿token o API caída?)" >&2
    return 0
  fi
  # `name{a="x",b="y"} 12.3` -> ts,name,"a=x;b=y",12.3  (se quitan application y profile: son constantes)
  awk -v ts="${ts}" -v keep="${KEEP}" '
    /^#/ { next }
    {
      line = $0
      value = line; sub(/^.*[ ]/, "", value)
      head = line; sub(/[ ][^ ]*$/, "", head)
      name = head; labels = ""
      if (index(head, "{") > 0) {
        name = substr(head, 1, index(head, "{") - 1)
        labels = substr(head, index(head, "{") + 1); sub(/}$/, "", labels)
      }
      if (name !~ keep) next
      n = split(labels, parts, /",/)
      out = ""
      for (i = 1; i <= n; i++) {
        p = parts[i]; gsub(/"/, "", p)
        if (p ~ /^(application|profile)=/ || p == "") continue
        out = out (out == "" ? "" : ";") p
      }
      printf "%s,%s,\"%s\",%s\n", ts, name, out, value
    }' <<<"${body}" >> "${OUT}"
}

stop=0
trap 'stop=1' INT TERM
start=$(date +%s)
scrape
if [ "${DURATION}" -gt 0 ]; then
  while [ "${stop}" -eq 0 ] && [ $(( $(date +%s) - start )) -lt "${DURATION}" ]; do
    sleep "${INTERVAL}" & wait $! || true
    [ "${stop}" -eq 0 ] && scrape
  done
fi
echo "listo: ${OUT} ($(($(wc -l < "${OUT}") - 1)) filas)" >&2
