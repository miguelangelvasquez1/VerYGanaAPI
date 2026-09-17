#!/usr/bin/env bash
#
# Sube monitoring/prometheus/alert.rules.yml a Grafana Cloud como reglas
# Grafana-managed.
#
# POR QUÉ EXISTE ESTE SCRIPT Y NO UN `mimirtool rules load` PELADO
# ----------------------------------------------------------------
# Las anotaciones están escritas en el dialecto de Prometheus
# ({{ $value | humanizeDuration }}), que es el que entiende el Prometheus local
# y el que valida `promtool` en el CI. Grafana usa otro: allá $value no es un
# número sino un texto con todas las series y sus etiquetas, así que pasarlo por
# humanizeDuration imprime un error en vez de "3 días". El equivalente correcto
# es {{ humanizeDuration $values.query.Value }} — pero esa forma NO parsea en
# Prometheus (error "undefined variable $values") y rompería el CI.
#
# Los dos dialectos son incompatibles y el archivo tiene que seguir siendo
# válido para Prometheus, así que la traducción se hace acá, en el momento de
# subir. El repo sigue teniendo una sola fuente de verdad.
#
# Tampoco se usa mimirtool: valida el archivo del lado del cliente con el parser
# de Prometheus y rechaza $values antes de mandar nada. El endpoint del servidor
# sí lo acepta, así que se postea con curl.
#
# USO
# ---
#   export GRAFANA_URL=https://<tu-stack>.grafana.net
#   export GRAFANA_SA_TOKEN=glsa_...        # service account token de la instancia
#   export GRAFANA_PROM_DS_UID=grafanacloud-prom
#   ./monitoring/upload-rules.sh
#
# Las reglas quedan marcadas como provisionadas: read-only en la UI. Es
# deliberado — se editan en el archivo y se vuelve a correr esto.

set -euo pipefail

RULES_FILE="${RULES_FILE:-$(dirname "$0")/prometheus/alert.rules.yml}"
NAMESPACE="${NAMESPACE:-verygana}"

: "${GRAFANA_URL:?falta GRAFANA_URL (https://<stack>.grafana.net)}"
: "${GRAFANA_SA_TOKEN:?falta GRAFANA_SA_TOKEN (service account token)}"
: "${GRAFANA_PROM_DS_UID:?falta GRAFANA_PROM_DS_UID (uid del datasource de Prometheus)}"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

# Traduce las anotaciones y parte el archivo en un YAML por grupo: el endpoint
# recibe un grupo por petición.
python3 - "$RULES_FILE" "$WORK_DIR" <<'PY'
import re, sys, yaml

src, work_dir = sys.argv[1], sys.argv[2]

# refId "query" es el nombre que le pone el conversor de Grafana a la consulta
# PromQL original; .Value es su valor numérico.
VALUE = "$values.query.Value"
SUSTITUCIONES = [
    (r'{{ \$value \| (humanizeDuration|humanizePercentage|humanize) }}', r'{{ \1 ' + VALUE + ' }}'),
    (r'{{ \$value \| printf \\"([^"]+)\\" }}', r'{{ printf \\"\1\\" ' + VALUE + ' }}'),
    (r'{{ \$value \| printf "([^"]+)" }}', r'{{ printf "\1" ' + VALUE + ' }}'),
    (r'{{ \$value }}', '{{ ' + VALUE + ' }}'),
]

def traducir(texto):
    for patron, reemplazo in SUSTITUCIONES:
        texto = re.sub(patron, reemplazo, texto)
    return texto

doc = yaml.safe_load(open(src, encoding="utf-8"))
total = 0
for grupo in doc["groups"]:
    for regla in grupo.get("rules", []):
        for clave, valor in (regla.get("annotations") or {}).items():
            nuevo = traducir(valor)
            if nuevo != valor:
                total += 1
            regla["annotations"][clave] = nuevo
    destino = f"{work_dir}/{grupo['name']}.yml"
    with open(destino, "w", encoding="utf-8") as fh:
        yaml.safe_dump(grupo, fh, allow_unicode=True, sort_keys=False)

print(f"{len(doc['groups'])} grupos, {total} anotaciones traducidas al dialecto de Grafana")
PY

for archivo in "$WORK_DIR"/*.yml; do
    grupo="$(basename "$archivo" .yml)"
    printf '  %-16s ' "$grupo"
    codigo=$(curl -s -o "$WORK_DIR/respuesta" -w '%{http_code}' -X POST \
        -H "Authorization: Bearer ${GRAFANA_SA_TOKEN}" \
        -H "Content-Type: application/yaml" \
        -H "X-Grafana-Alerting-Datasource-UID: ${GRAFANA_PROM_DS_UID}" \
        --data-binary "@${archivo}" \
        "${GRAFANA_URL}/api/convert/prometheus/config/v1/rules/${NAMESPACE}")

    if [ "$codigo" = "202" ] || [ "$codigo" = "200" ]; then
        echo "OK ($codigo)"
    else
        echo "FALLÓ ($codigo)"
        cat "$WORK_DIR/respuesta"
        echo
        exit 1
    fi
done

echo "Listo. Verificar en ${GRAFANA_URL}/alerting/list"
