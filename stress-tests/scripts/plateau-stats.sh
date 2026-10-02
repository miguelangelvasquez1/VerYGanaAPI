#!/usr/bin/env bash
# Resume una corrida local para llenar el informe: CPU y memoria de cada contenedor en la MESETA,
# métricas de la API en la meseta, y el veredicto de los umbrales y de la marca "sesgada".
# Lee lo que dejó run-scenario.sh en la carpeta de resultados; no toca la base ni la API.
#
# Uso: plateau-stats.sh <carpeta-de-resultados> <A|B> [DOCKER_CPUS]
#   La meseta se cuenta desde la primera muestra del contenedor k6 (arranque de la prueba):
#   A = minutos 5 a 25; B = minutos 24 a 44 (los 20 min a 2.000 VUs). Con DURATION_SCALE las
#   ventanas se escalan igual que las etapas de k6 (hay que pasarlo igual que en la corrida).
#   Sesgos que marca: CPU de k6 > 70 %, memoria de k6 > 90 % de su límite (con el tope encima, k6 se estrangula
#   y la carga que genera deja de ser la de la etapa), suma de CPU > 85 % y dropped_iterations > 0.
#   DOCKER_CPUS (por defecto, lo que dice `docker info`) sirve para la suma de CPU de todos los contenedores.
# CPU de la BD = cpu_pct del contenedor mysql ÷ su límite de núcleos (la columna cpu_pct_of_limit).
set -uo pipefail

DIR="${1:?uso: plateau-stats.sh <carpeta> <A|B> [DOCKER_CPUS]}"
SCEN="${2:?uso: plateau-stats.sh <carpeta> <A|B> [DOCKER_CPUS]}"
DOCKER_CPUS="${3:-$(docker info --format '{{.NCPU}}' 2>/dev/null || echo 0)}"
SCALE="${DURATION_SCALE:-1}"
case "${SCEN}" in A) FROM=5; TO=25 ;; B) FROM=24; TO=44 ;; *) echo "escenario inválido: ${SCEN}" >&2; exit 2 ;; esac
STATS="${DIR}/docker-stats.csv"
APIM="${DIR}/api-metrics.csv"
[ -s "${STATS}" ] || { echo "no hay ${STATS}" >&2; exit 2; }

EPOCH_AWK='
function epoch(ts,   y, m, d, h, mi, s, a, yy, mm, days) {
  y = substr(ts, 1, 4) + 0; m = substr(ts, 6, 2) + 0; d = substr(ts, 9, 2) + 0
  h = substr(ts, 12, 2) + 0; mi = substr(ts, 15, 2) + 0; s = substr(ts, 18, 2) + 0
  a = (m <= 2) ? 1 : 0; yy = y - a; mm = m + 12 * a - 3
  days = 365 * yy + int(yy / 4) - int(yy / 100) + int(yy / 400) + int((153 * mm + 2) / 5) + d
  return days * 86400 + h * 3600 + mi * 60 + s
}'

# Arranque de k6 = primera muestra del servicio k6 (si no hay, la primera fila).
T0="$(awk -F, 'NR > 1 && $2 == "k6" { print $1; exit }' "${STATS}")"
[ -n "${T0}" ] || T0="$(awk -F, 'NR == 2 { print $1 }' "${STATS}")"
WIN_FROM="$(awk -v t0="${T0}" -v f="${FROM}" -v sc="${SCALE}" "${EPOCH_AWK}"' BEGIN { printf "%d", epoch(t0) + f * 60 * sc }')"
WIN_TO="$(awk -v t0="${T0}" -v t="${TO}" -v sc="${SCALE}" "${EPOCH_AWK}"' BEGIN { printf "%d", epoch(t0) + t * 60 * sc }')"

echo "# plateau-stats: ${DIR##*/} escenario=${SCEN} meseta=min ${FROM}-${TO} (escala ${SCALE}) desde k6=${T0}"
echo
echo "== CPU y memoria por contenedor en la meseta =="
awk -F, -v wf="${WIN_FROM}" -v wt="${WIN_TO}" "${EPOCH_AWK}"'
  NR > 1 {
    t = epoch($1); if (t < wf || t > wt) next
    s = $2; n[s]++; cpu[s] += $3; lim[s] = $4; pl[s] += $5; mem[s] += $6; memlim[s] = $7
    if ($5 + 0 > peak[s]) peak[s] = $5 + 0
    ts[$1] += $3
  }
  END {
    printf "%-10s %8s %12s %14s %14s %12s\n", "servicio", "muestras", "cpu_%_prom", "cpu_%_límite", "cpu_%_lím_pico", "mem_MiB_prom"
    for (s in n) printf "%-10s %8d %12.1f %14.1f %14.1f %12.1f\n", s, n[s], cpu[s] / n[s], pl[s] / n[s], peak[s], mem[s] / n[s]
    # suma de CPU por muestra (100 = un núcleo): promedio de las sumas
    k = 0; tot = 0; for (x in ts) { tot += ts[x]; k++ }
    printf "\nsuma de CPU de todos los contenedores (promedio): %.1f %% de un núcleo\n", (k ? tot / k : 0)
  }' "${STATS}"

MYSQL_AVG="$(awk -F, -v wf="${WIN_FROM}" -v wt="${WIN_TO}" "${EPOCH_AWK}"' NR > 1 && $2 == "mysql" { t = epoch($1); if (t >= wf && t <= wt) { s += $5; n++ } } END { if (n) printf "%.1f", s / n; else print "NA" }' "${STATS}")"
K6_AVG="$(awk -F, -v wf="${WIN_FROM}" -v wt="${WIN_TO}" "${EPOCH_AWK}"' NR > 1 && $2 == "k6" { t = epoch($1); if (t >= wf && t <= wt) { s += $5; n++ } } END { if (n) printf "%.1f", s / n; else print "NA" }' "${STATS}")"
K6_MEMPCT="$(awk -F, -v wf="${WIN_FROM}" -v wt="${WIN_TO}" "${EPOCH_AWK}"' NR > 1 && $2 == "k6" { t = epoch($1); if (t >= wf && t <= wt) { s += $8; n++; if ($8 + 0 > pk) pk = $8 + 0 } } END { if (n) printf "%.1f", s / n; else print "NA" }' "${STATS}")"
K6_MEMPEAK="$(awk -F, -v wf="${WIN_FROM}" -v wt="${WIN_TO}" "${EPOCH_AWK}"' NR > 1 && $2 == "k6" { t = epoch($1); if (t >= wf && t <= wt && $8 + 0 > pk) pk = $8 + 0; if (t >= wf && t <= wt) n++ } END { if (n) printf "%.1f", pk; else print "NA" }' "${STATS}")"
K6_MEMMIB="$(awk -F, -v wf="${WIN_FROM}" -v wt="${WIN_TO}" "${EPOCH_AWK}"' NR > 1 && $2 == "k6" { t = epoch($1); if (t >= wf && t <= wt) { s += $6; n++; lim = $7 } } END { if (n) printf "%.0f MiB de %.0f MiB", s / n, lim; else print "NA" }' "${STATS}")"
SUM_PCT="$(awk -F, -v wf="${WIN_FROM}" -v wt="${WIN_TO}" -v cpus="${DOCKER_CPUS}" "${EPOCH_AWK}"'
  NR > 1 { t = epoch($1); if (t >= wf && t <= wt) { sum[$1] += $3 } }
  END { k = 0; tot = 0; for (x in sum) { tot += sum[x]; k++ } if (k && cpus > 0) printf "%.1f", tot / k / (cpus * 100) * 100; else print "NA" }' "${STATS}")"

echo
echo "== API en la meseta (api-metrics.csv) =="
if [ -s "${APIM}" ]; then
  awk -F, -v wf="${WIN_FROM}" -v wt="${WIN_TO}" "${EPOCH_AWK}"'
    NR > 1 {
      t = epoch($1); if (t < wf || t > wt) next
      m = $2; v = $NF + 0
      if (m == "process_cpu_usage") { c += v; cn++; if (v > cp) cp = v }
      else if (m == "hikaricp_connections_pending") { if (v > hp) hp = v }
      else if (m == "hikaricp_connections_active") { if (v > ha) ha = v }
      else if (m == "hikaricp_connections_max") { hm = v }
      else if (m == "tomcat_threads_busy_threads") { if (v > tb) tb = v }
      else if (m == "tomcat_threads_config_max_threads") { tm = v }
    }
    END {
      printf "cpu_api_prom=%.1f%% cpu_api_pico=%.1f%% (fracción de TODOS los núcleos que ve la JVM)\n", (cn ? c / cn * 100 : 0), cp * 100
      printf "hikari_activas_pico=%d de %d, hikari_pendientes_pico=%d\n", ha, hm, hp
      printf "tomcat_ocupados_pico=%d de %d\n", tb, tm
    }' "${APIM}"
else
  echo "(sin api-metrics.csv)"
fi

echo
echo "== Resumen de k6 y veredicto =="
SUMMARY="$(find "${DIR}" -maxdepth 1 -name 'summary-*.json' | sort | tail -n 1)"
P95="NA"; ERR="NA"; DROP="NA"
if [ -n "${SUMMARY}" ]; then
  read -r P95 ERR DROP < <(awk '/"global"/ { g = 1 } g && /"p95"/ { gsub(/[",]/, "", $2); p = $2 } g && /"errorRate"/ { gsub(/[",]/, "", $2); e = $2 } g && /"droppedIterations"/ { gsub(/[",]/, "", $2); d = $2; exit } END { print p, e, d }' "${SUMMARY}")
  echo "p95 global=${P95} ms, error=${ERR} (fracción), dropped_iterations=${DROP}"
fi
MAXCONN="NA"; MAXMAX="NA"; REFUSED="NA"
if [ -s "${DIR}/snapshot-after.tsv" ]; then
  MAXCONN="$(awk -F'\t' '$1 == "status.Max_used_connections" { print $2 }' "${DIR}/snapshot-after.tsv")"
  MAXMAX="$(awk -F'\t' '$1 == "variable.max_connections" { print $2 }' "${DIR}/snapshot-after.tsv")"
  REFUSED="$(awk -F'\t' '$1 == "status.Connection_errors_max_connections" { print $2 }' "${DIR}/snapshot-after.tsv")"
fi
echo "conexiones: máximo usado=${MAXCONN} de ${MAXMAX}; rechazadas por el máximo=${REFUSED}"
echo "cpu_bd_prom=${MYSQL_AVG} % del límite; cpu_k6_prom=${K6_AVG} % de su límite; suma de CPU=${SUM_PCT} % de las CPU de Docker (${DOCKER_CPUS})"

yn() { awk -v v="$1" -v op="$2" -v lim="$3" 'BEGIN { if (v == "NA") print "sin dato"; else if (op == "<" ? v + 0 < lim + 0 : v + 0 > lim + 0) print "sí"; else print "no" }'; }
echo
echo "memoria de k6 en la meseta: prom=${K6_MEMMIB} (${K6_MEMPCT} % del límite), pico=${K6_MEMPEAK} %"
echo "Umbral  p95 < 1000 ms:        $(yn "${P95}" '<' 1000)"
echo "Umbral  error < 1 %:          $(yn "${ERR}" '<' 0.01)"
echo "Umbral  CPU BD prom < 75 %:   $(yn "${MYSQL_AVG}" '<' 75)"
if [ "${MAXCONN}" != "NA" ] && [ "${MAXMAX}" != "NA" ]; then
  awk -v u="${MAXCONN}" -v m="${MAXMAX}" -v r="${REFUSED:-0}" 'BEGIN { print "Umbral  sin agotar conexiones:   " ((u + 0 < m + 0 && r + 0 == 0) ? "sí" : "no") }'
else
  echo "Umbral  sin agotar conexiones:   sin dato"
fi
echo "sesgo  k6 > 70 % de su CPU:  $(yn "${K6_AVG}" '>' 70)"
echo "sesgo  k6 memoria > 90 %:    $(yn "${K6_MEMPCT}" '>' 90)"
echo "sesgo  suma CPU > 85 %:      $(yn "${SUM_PCT}" '>' 85)"
echo "sesgo  dropped_iterations>0: $(yn "${DROP}" '>' 0)"
echo
echo "Nota: el p95 y el error son de TODA la corrida (k6), no solo de la meseta; el resto es de la meseta."
