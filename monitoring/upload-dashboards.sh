#!/usr/bin/env bash
#
# Sube los dashboards de monitoring/grafana/dashboards/*.json a Grafana (Cloud)
# en la carpeta VERyGANA, con overwrite. Gemelo de upload-rules.sh: el repo
# sigue siendo la fuente de verdad y los dashboards se editan en el archivo.
#
# Los JSON usan la variable de datasource DS_PROM (tipo datasource), así que no
# hay nada que sustituir: en el selector se elige grafanacloud-prom.
#
# USO
# ---
#   export GRAFANA_URL=https://<tu-stack>.grafana.net
#   export GRAFANA_SA_TOKEN=glsa_...   # service account con escritura de dashboards
#   ./monitoring/upload-dashboards.sh
#
# Salida: 0 todo subido; 1 Grafana rechazó algún dashboard (HTTP de error);
# 2 no se pudo hablar con Grafana (curl no conectó o agotó el tiempo).
#
# El token va solo en una cabecera leída desde un archivo temporal (-H @archivo):
# no aparece en la línea de comandos, ni en la URL, ni en la salida, ni con set -x.

set -euo pipefail
# Sin traza: con `bash -x` el token aparecería en cada expansión.
set +x

DASH_DIR="${DASH_DIR:-$(dirname "$0")/grafana/dashboards}"
FOLDER_TITLE="${FOLDER_TITLE:-VERyGANA}"

: "${GRAFANA_URL:?falta GRAFANA_URL (https://<stack>.grafana.net)}"
: "${GRAFANA_SA_TOKEN:?falta GRAFANA_SA_TOKEN (service account token)}"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

# Cabecera de autenticación en un archivo (modo 600); printf es un builtin y no
# deja el token en la lista de procesos.
umask 077
printf 'Authorization: Bearer %s\n' "$GRAFANA_SA_TOKEN" > "$WORK_DIR/auth"

# Llama a Grafana. Deja el cuerpo en $WORK_DIR/respuesta y el código HTTP en
# $HTTP_CODE. Si curl no conecta, sale 2 (no es un rechazo de Grafana).
llamar() {
    local metodo="$1" ruta="$2" cuerpo="${3:-}"
    local args=(-s --max-time 30 -o "$WORK_DIR/respuesta" -w '%{http_code}' -X "$metodo"
        -H "@$WORK_DIR/auth" -H "Content-Type: application/json")
    if [ -n "$cuerpo" ]; then
        args+=(--data-binary "@$cuerpo")
    fi
    local rc=0
    HTTP_CODE=$(curl "${args[@]}" "${GRAFANA_URL}${ruta}") || rc=$?
    if [ "$rc" -ne 0 ]; then
        echo "No se pudo hablar con Grafana (curl salió con $rc)." >&2
        exit 2
    fi
}

# Busca la carpeta por título; si no existe, la crea. Devuelve su uid.
llamar GET "/api/folders?limit=1000"
if [ "$HTTP_CODE" != "200" ]; then
    echo "Grafana rechazó listar carpetas ($HTTP_CODE):" >&2
    cat "$WORK_DIR/respuesta" >&2; echo >&2
    exit 1
fi
FOLDER_UID="$(python3 -B - "$WORK_DIR/respuesta" "$FOLDER_TITLE" <<'PY'
import json, sys
for carpeta in json.load(open(sys.argv[1], encoding="utf-8")):
    if carpeta.get("title") == sys.argv[2]:
        print(carpeta["uid"])
        break
PY
)"

if [ -z "$FOLDER_UID" ]; then
    python3 -B -c 'import json,sys; json.dump({"title": sys.argv[1]}, open(sys.argv[2], "w"))' \
        "$FOLDER_TITLE" "$WORK_DIR/carpeta.json"
    llamar POST "/api/folders" "$WORK_DIR/carpeta.json"
    if [ "$HTTP_CODE" != "200" ]; then
        echo "Grafana rechazó crear la carpeta ($HTTP_CODE):" >&2
        cat "$WORK_DIR/respuesta" >&2; echo >&2
        exit 1
    fi
    FOLDER_UID="$(python3 -B -c 'import json,sys; print(json.load(open(sys.argv[1]))["uid"])' "$WORK_DIR/respuesta")"
fi

subidos=0
for archivo in "$DASH_DIR"/*.json; do
    nombre="$(basename "$archivo")"
    printf '  %-22s ' "$nombre"
    # Envoltorio de /api/dashboards/db; id nulo para que Grafana resuelva por uid.
    python3 -B - "$archivo" "$FOLDER_UID" "$WORK_DIR/dashboard.json" <<'PY'
import json, sys
dash = json.load(open(sys.argv[1], encoding="utf-8"))
dash["id"] = None
json.dump({"dashboard": dash, "folderUid": sys.argv[2], "overwrite": True,
           "message": "upload-dashboards.sh"},
          open(sys.argv[3], "w", encoding="utf-8"))
PY
    llamar POST "/api/dashboards/db" "$WORK_DIR/dashboard.json"
    if [ "$HTTP_CODE" = "200" ]; then
        echo "OK ($HTTP_CODE)"
        subidos=$((subidos + 1))
    else
        echo "FALLÓ ($HTTP_CODE)"
        cat "$WORK_DIR/respuesta" >&2; echo >&2
        exit 1
    fi
done

echo "Listo: $subidos dashboards en la carpeta ${FOLDER_TITLE}. Verificar en ${GRAFANA_URL}/dashboards"
