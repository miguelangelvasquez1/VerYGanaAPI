#!/usr/bin/env bash
# Corre un archivo de stress-tests/db/ contra la base y escribe la salida en un archivo (o en
# pantalla). Es el punto de entrada de snapshot.sql, top-digests.sql, table-sizes.sql, tls-check.sql,
# check-seed.sql y check-schema.sql en las dos fases (LT_MODE=local o direct, ver common.sh).
#
# Uso: run-sql.sh <archivo.sql> [salida]
#   snapshot.sql se guarda en TSV (--batch) para que snapshot-delta.sh lo pueda restar; el resto,
#   en tabla legible. Usuario: MYSQL_USER (verygana_monitor por defecto).
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

FILE="${1:?uso: run-sql.sh <archivo.sql> [salida]}"
OUT="${2:-}"
[ -f "${FILE}" ] || FILE="${STRESS_DIR}/db/${FILE}"

case "$(basename "${FILE}")" in
  snapshot.sql) MYSQL_FMT="--batch" ;;
esac

if [ -n "${OUT}" ]; then
  mkdir -p "$(dirname "${OUT}")"
  run_sql "${FILE}" > "${OUT}"
  echo "listo: ${OUT}" >&2
else
  run_sql "${FILE}"
fi
