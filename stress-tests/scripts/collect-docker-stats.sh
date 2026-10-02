#!/usr/bin/env bash
# Muestrea `docker stats --no-stream` cada N segundos para los contenedores del proyecto
# verygana-loadtest (mysql, api, k6, wiremock, minio) y los guarda en CSV.
# Es la fuente local de la CPU y la memoria de la BD y de la CPU de k6.
#
# Columnas: ts, service, cpu_pct (100 = un núcleo, como docker stats), cpu_limit (núcleos del
# contenedor), cpu_pct_of_limit (cpu_pct / (cpu_limit*100); el umbral de CPU de MySQL usa este valor),
# mem_mib, mem_limit_mib, mem_pct, blk_read_mb, blk_write_mb (acumulados), net_rx_mb, net_tx_mb.
#
# Uso: collect-docker-stats.sh <salida.csv> [intervalo_s=5] [duracion_s=0]
#   duración 0 = hasta Ctrl-C/SIGTERM. El contenedor de k6 de `run --rm` aparece cuando existe:
#   se busca por la etiqueta de compose en cada muestra, no por nombre fijo.
set -euo pipefail

OUT="${1:?uso: collect-docker-stats.sh <salida.csv> [intervalo_s] [duracion_s]}"
INTERVAL="${2:-5}"
DURATION="${3:-0}"
PROJECT="${COMPOSE_PROJECT:-verygana-loadtest}"
SERVICES=(mysql api k6 wiremock minio)

mkdir -p "$(dirname "${OUT}")"
[ -s "${OUT}" ] || echo "ts,service,cpu_pct,cpu_limit,cpu_pct_of_limit,mem_mib,mem_limit_mib,mem_pct,blk_read_mb,blk_write_mb,net_rx_mb,net_tx_mb" > "${OUT}"

sample() {
  local ts svc id ids="" map="" limit
  ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  # Una sola llamada a docker stats para todos los contenedores (cada llamada tarda segundos).
  for svc in "${SERVICES[@]}"; do
    for id in $(docker ps -q --filter "label=com.docker.compose.project=${PROJECT}" \
                              --filter "label=com.docker.compose.service=${svc}"); do
      limit="$(docker inspect --format '{{.HostConfig.NanoCpus}}' "${id}")"
      ids="${ids} ${id}"
      map="${map}${id}=${svc}:${limit};"
    done
  done
  [ -n "${ids}" ] || return 0
  # shellcheck disable=SC2086
  docker stats --no-stream --format '{{.ID}}|{{.CPUPerc}}|{{.MemUsage}}|{{.MemPerc}}|{{.BlockIO}}|{{.NetIO}}' ${ids} \
    | awk -F'|' -v ts="${ts}" -v map="${map}" '
      function bytes(s,   v, u) {            # "12.5MiB" -> bytes
        gsub(/[ \t]/, "", s)
        v = s + 0; u = s; sub(/^[0-9.]+/, "", u)
        if (u == "B") return v
        if (u == "kB" || u == "KB") return v * 1000
        if (u == "MB") return v * 1000000
        if (u == "GB") return v * 1000000000
        if (u == "KiB") return v * 1024
        if (u == "MiB") return v * 1048576
        if (u == "GiB") return v * 1073741824
        return v
      }
      BEGIN {
        n = split(map, entries, ";")
        for (i = 1; i <= n; i++) if (entries[i] != "") {
          split(entries[i], kv, "="); split(kv[2], sv, ":")
          svc[kv[1]] = sv[1]; nano[kv[1]] = sv[2]
        }
      }
      {
        id = $1; cpu = $2; sub(/%/, "", cpu); mp = $4; sub(/%/, "", mp)
        # docker stats imprime el ID corto; el de docker ps -q también es corto
        key = id
        if (!(key in svc)) next
        split($3, m, "/"); split($5, blk, "/"); split($6, net, "/")
        lim = nano[key] / 1e9
        printf "%s,%s,%.2f,%s,%s,%.1f,%.1f,%.2f,%.2f,%.2f,%.2f,%.2f\n", ts, svc[key], cpu, \
          (lim > 0 ? sprintf("%.2f", lim) : ""), (lim > 0 ? sprintf("%.1f", cpu / (lim * 100) * 100) : ""), \
          bytes(m[1]) / 1048576, bytes(m[2]) / 1048576, mp, \
          bytes(blk[1]) / 1e6, bytes(blk[2]) / 1e6, bytes(net[1]) / 1e6, bytes(net[2]) / 1e6
      }' >> "${OUT}"
}

stop=0
trap 'stop=1' INT TERM
start=$(date +%s)
while [ "${stop}" -eq 0 ]; do
  t0=$(date +%s)
  sample
  [ "${DURATION}" -gt 0 ] && [ $(( $(date +%s) - start )) -ge "${DURATION}" ] && break
  elapsed=$(( $(date +%s) - t0 ))
  rest=$(( INTERVAL - elapsed )); [ "${rest}" -gt 0 ] || rest=0
  sleep "${rest}" & wait $! || true
done
echo "listo: ${OUT} ($(($(wc -l < "${OUT}") - 1)) filas)" >&2
