#!/usr/bin/env bash
# Comprueba la sección "Impacto en el front" de la spec de la API (RN-9 de la spec 010).
#
# Uso:  scripts/check-front-impact.sh specs/<NNN>-<slug>
#
# Solo lectura: no escribe nada y no imprime el contenido de la spec (solo rutas y motivos
# del resultado). Compatible con bash 3.2 (macOS) y Git Bash.
#
# Formato que lee (líneas fijas dentro de "## Impacto en el front"):
#   - **Toca al front:** Sí | No
#   - **Motivo o cambios:** <texto>
#   - **Spec del front:** <ruta de spec.md> | PENDIENTE DE TRASLADAR (front-spec.md) | No aplica
#
# Ruta de "Spec del front": si va entre comillas invertidas se toma lo que hay dentro; si no,
# el valor completo. Si es absoluta (empieza por "/" o por una letra de unidad "C:") se usa
# tal cual; si es relativa se resuelve contra VERYGANA_FRONT_DIR. Con una ruta relativa y sin
# esa variable definida no se inventa ninguna ruta: sale con 1.
#
# Para que "Sí" valga, la referencia tiene que ser una spec real del repo del front:
#   - la sección "## Impacto en el front" aparece una sola vez, y cada uno de sus tres campos
#     (Toca al front, Motivo o cambios, Spec del front) también;
#   - la ruta real de la referencia (resuelta con `cd` y `pwd -P`, o sea, siguiendo enlaces
#     simbólicos) no cae dentro del repo de la API, que es dos niveles arriba de la spec que se
#     comprueba;
#   - la ruta termina en specs/<NNN>-<slug>/spec.md con NNN numérico (no _template, no
#     front-spec.md, no el spec.md de la propia spec de la API);
#   - dos niveles arriba de specs/ hay un package.json con "name": "rifacel-app-nextjs";
#   - su Estado es un solo valor (Borrador, Aprobada o Implementada), no la línea sin llenar;
#   - su Origen nombra la carpeta de la spec de la API que se comprueba (specs/<NNN>-<slug>).
#
# Códigos de salida:
#   0  "No" con motivo; o "Sí" con una referencia que cumple todo lo de arriba.
#   1  Cualquier otro caso: sección ausente, repetida o sin llenar, campo repetido, "No" sin motivo, "Sí" sin
#      referencia, PENDIENTE DE TRASLADAR, archivo inexistente, ruta relativa sin
#      VERYGANA_FRONT_DIR, ruta o repo que no son una spec del front (o que caen dentro de la
#      API), Estado inválido u Origen
#      que no nombra esta spec de la API.
#   2  Mal uso: sin argumento, o la carpeta no tiene spec.md.

if [ $# -ne 1 ]; then
  echo "Uso: $0 specs/<NNN>-<slug>" >&2
  exit 2
fi

dir=${1%/}
spec="$dir/spec.md"
if [ ! -f "$spec" ]; then
  echo "No existe $spec" >&2
  exit 2
fi

fail() { echo "FALLA ($spec): $1" >&2; exit 1; }

# Texto de un campo de la sección, sin la etiqueta y sin espacios en los extremos.
field() {
  printf '%s\n' "$section" | grep -m1 -E "^- \*\*$1:\*\*" \
    | sed -E "s/^- \*\*$1:\*\*[[:space:]]*//; s/[[:space:]]+\$//"
}

section_count=$(grep -c -E '^## Impacto en el front[[:space:]]*$' "$spec")
[ "$section_count" -le 1 ] \
  || fail "la sección '## Impacto en el front' aparece $section_count veces; debe aparecer una sola."

section=$(awk '
  /^## Impacto en el front[[:space:]]*$/ { inside = 1; next }
  /^## / { inside = 0 }
  inside { print }
' "$spec")

[ -n "$section" ] || fail "falta la sección '## Impacto en el front'."

# Cada campo aparece una sola vez: con dos líneas iguales, `grep -m1` leería la primera y callaría la otra.
for label in "Toca al front" "Motivo o cambios" "Spec del front"; do
  n=$(printf '%s\n' "$section" | grep -c -E "^- \*\*$label:\*\*")
  [ "$n" -le 1 ] || fail "el campo '$label' aparece $n veces en la sección; debe aparecer una sola."
done

touches=$(field "Toca al front")
reason=$(field "Motivo o cambios")
ref=$(field "Spec del front")

# Sin distinguir mayúsculas (bash 3.2 de macOS: sin ${var,,}). `tr` solo baja ASCII, así que
# "SÍ" queda como "sÍ" y se admite aparte.
touches_lc=$(printf '%s' "$touches" | tr 'A-Z' 'a-z')
case "$touches_lc" in
  "no")
    [ -n "$reason" ] || fail "'Toca al front: No' exige el motivo en 'Motivo o cambios'."
    echo "OK ($spec): no toca al front, con motivo escrito."
    exit 0
    ;;
  "sí" | "si" | "sÍ")
    ;;
  "")
    fail "falta la línea '- **Toca al front:**'."
    ;;
  *)
    fail "'Toca al front' debe ser exactamente 'Sí' o 'No' (está sin llenar o mal escrito)."
    ;;
esac

[ -n "$reason" ] || fail "'Toca al front: Sí' exige describir los cambios en 'Motivo o cambios'."
[ -n "$ref" ] || fail "'Toca al front: Sí' exige 'Spec del front'; está vacío."

case "$ref" in
  PENDIENTE*)
    fail "la spec del front está PENDIENTE DE TRASLADAR; mueve front-spec.md al repo del front y actualiza la referencia."
    ;;
  "No aplica"*)
    fail "'Toca al front: Sí' no puede tener 'Spec del front: No aplica'."
    ;;
esac

# Valor entre comillas invertidas, si las hay.
case "$ref" in
  \`*\`*) path=$(printf '%s' "$ref" | sed -E 's/^`([^`]*)`.*/\1/') ;;
  *) path=$ref ;;
esac

case "$path" in
  /* | [A-Za-z]:[/\\]*)
    full=$path
    ;;
  *)
    [ -n "${VERYGANA_FRONT_DIR:-}" ] \
      || fail "la ruta de la spec del front es relativa y VERYGANA_FRONT_DIR no está definida."
    full="${VERYGANA_FRONT_DIR%/}/$path"
    ;;
esac

[ -f "$full" ] || fail "no existe la spec del front referenciada: $full"

# La referencia debe tener la forma <repo>/specs/<NNN>-<slug>/spec.md con NNN numérico. Esto
# deja fuera _template, front-spec.md y cualquier archivo que no sea la spec de una funcionalidad.
norm=$(printf '%s' "$full" | tr '\\' '/')
case "/$norm/" in
  */../*) fail "la ruta de la spec del front no puede tener '..': $full" ;;
esac
printf '%s\n' "$norm" | grep -q -E '/specs/[0-9]+-[^/]+/spec\.md$' \
  || fail "la referencia debe terminar en specs/<NNN>-<slug>/spec.md con NNN numérico (no _template ni front-spec.md): $full"

# La spec del front no puede estar dentro del repo de la API (ni por ruta ni por enlace simbólico):
# un package.json fabricado dentro de la API no es el repo del front. Se compara la ruta física.
api_root=$(cd "$dir/../.." 2>/dev/null && pwd -P)
ref_dir=$(cd "$(dirname "$full")" 2>/dev/null && pwd -P)
# La comparación ignora mayúsculas: el disco de macOS no las distingue y una ruta con otras
# mayúsculas (`.../VERYGANAR/...`) apuntaría a la misma carpeta.
if [ -n "$api_root" ] && [ -n "$ref_dir" ]; then
  api_root_lc=$(printf '%s' "$api_root" | tr 'A-Z' 'a-z')
  ref_dir_lc=$(printf '%s' "$ref_dir" | tr 'A-Z' 'a-z')
  case "$ref_dir_lc/" in
    "$api_root_lc"/*) fail "la spec del front referenciada está dentro del repo de la API, no en el repo del front: $full" ;;
  esac
fi

# Dos niveles arriba de specs/ tiene que estar el repo del front (misma condición del coordinator).
front_root=$(printf '%s\n' "$norm" | sed -E 's#/specs/[0-9]+-[^/]+/spec\.md$##')
[ -f "$front_root/package.json" ] && grep -q '"name"[[:space:]]*:[[:space:]]*"rifacel-app-nextjs"' "$front_root/package.json" \
  || fail "la referencia no está en el repo del front (falta package.json con name rifacel-app-nextjs en $front_root)."

# Estado: un solo valor. La línea sin llenar de la plantilla ("Borrador | Aprobada | ...") no vale.
status=$(grep -m1 -E '^- \*\*Estado:\*\*' "$full" | sed -E 's/^- \*\*Estado:\*\*[[:space:]]*//; s/[[:space:]]+$//')
case "$status" in
  Borrador | Aprobada | Implementada) ;;
  *) fail "la spec del front no tiene un Estado válido (exactamente Borrador, Aprobada o Implementada): $full" ;;
esac

# Origen: debe nombrar la carpeta de esta spec de la API, para no aceptar la spec de otra funcionalidad.
api_name=$(basename "$dir")
origin=$(grep -m1 -E '^- \*\*Origen:\*\*' "$full" | sed -E 's/^- \*\*Origen:\*\*[[:space:]]*//; s/[[:space:]]+$//')
case "$origin" in
  *"specs/$api_name"*)
    rest=${origin#*"specs/$api_name"}
    case "$rest" in
      "" | [!A-Za-z0-9_-]*) ;;
      *) fail "el Origen de la spec del front no nombra specs/$api_name: $full" ;;
    esac
    ;;
  *) fail "el Origen de la spec del front no nombra specs/$api_name: $full" ;;
esac

echo "OK ($spec): toca al front y la spec del front existe: $full"
exit 0
