#!/usr/bin/env bash
# Verifica que las salidas de la prueba no contengan datos personales ni secretos:
# correos, celulares colombianos, cédulas, cuentas bancarias y tokens. Se corre sobre los logs
# de la API (`$LT logs --no-color api`), la salida de k6 y los archivos de results/.
#
# Uso: check-pii.sh <archivo|directorio|-> [...]     ("-" lee la entrada estándar)
# Salida: una línea "archivo:línea: tipo" por hallazgo. NUNCA imprime el texto encontrado, para no
# copiar el dato a otra salida. Código de salida: 0 limpio, 1 hay hallazgos, 2 uso incorrecto.
#
# Reglas (perl, que viene con macOS y con Linux):
#   correo     cualquier dirección usuario@dominio.tld (también @loadtest.invalid: ningún log debe traerla)
#   celular    +57 / separadores (3xx xxx xxxx) siempre; 10 dígitos pelados solo si la línea habla de
#              teléfono, celular o SMS (si no, contadores de bytes de 10 dígitos darían falsos positivos)
#   cedula     palabra clave (cédula, documento, NIT, CC...) seguida de 7 a 12 dígitos o puntos
#   cuenta     palabra clave (cuenta, account, IBAN, Nequi, Daviplata) seguida de 8 a 20 dígitos
#   token      JWT, "Bearer <token largo>" y password/secret/token/api-key con valor de 12+
#              caracteres que no empiece por "loadtest" ni "lt-" (literales ficticios del perfil y de la BD local)
#
# Excepciones nominales (lista cerrada):
#   F-GAMES-METRICS-STDOUT  GameController.java:91 hace System.out.println(event.toString()) en
#                           POST /games/metrics (hallazgo conocido, pendiente de arreglo). Se reconoce SOLO la
#                           línea completa, anclada al inicio (se admite solo el prefijo "servicio  | " de `docker compose logs`), con el toString() de GameEventDTO:
#                           "GameEventDTO(sessionToken=..., userHash=..., isBrandedMode=..., campaignId=..., ... technicalData=...)".
#                           De esa línea se borran únicamente los valores de sessionToken y userHash; el resto
#                           (payload, technicalData...) se sigue revisando, así que un correo o un token dentro del
#                           payload sí se marca. Una línea parecida con prefijo, o sin ese formato, no se excluye.
# Al final se imprime cuántas líneas excluyó cada excepción (nunca el dato).
set -uo pipefail

if [ "$#" -eq 0 ]; then
  echo "uso: check-pii.sh <archivo|directorio|-> [...]" >&2
  exit 2
fi

files=()
for arg in "$@"; do
  if [ "${arg}" = "-" ]; then
    files+=("-")
  elif [ -d "${arg}" ]; then
    while IFS= read -r f; do files+=("${f}"); done < <(find "${arg}" -type f | sort)
  elif [ -f "${arg}" ]; then
    files+=("${arg}")
  else
    echo "error: no existe ${arg}" >&2
    exit 2
  fi
done
if [ "${#files[@]}" -eq 0 ]; then
  echo "error: no hay archivos que revisar" >&2
  exit 2
fi

perl -e '
  my $found = 0;
  my $kw_phone = qr/tel[eé]fono|telefono|phone|celular|m[oó]vil|movil|sms|whatsapp|msisdn/i;
  my %rules = (
    correo  => qr/[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}/,
    celular => qr/(?<![\d.,])(?:\+?57[ -]?)3(?:0\d|1\d|2[0-4]|5[01]|99)[ -]?\d{3}[ -]?\d{4}(?!\d)
                 |(?<![\d.,])3(?:0\d|1\d|2[0-4]|5[01]|99)[ -]\d{3}[ -]\d{4}(?!\d)
                 |(?<![\d.,])3(?:0\d|1\d|2[0-4]|5[01]|99)-?\d{3}-\d{4}(?!\d)/x,
    cedula  => qr/\b(?:c(?:e|\xc3\xa9)dula|documento|document(?:_?number|_?id)?|identificaci(?:o|\xc3\xb3)n|identification|nit|c\.?c\.?)\b[^0-9\n]{0,20}\d[\d.]{5,12}\d/i,
    cuenta  => qr/\b(?:cuenta|account(?:_?number)?|iban|nequi|daviplata)\b[^0-9\n]{0,20}\d{8,20}/i,
    token   => qr/eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]*
                 |Bearer\s+(?!loadtest)[A-Za-z0-9._~+\/=-]{20,}
                 |(?:password|passwd|secret|api[_-]?key|token)\w*\s*[=:]\s*["\x27]?(?!loadtest|lt-)[^\s"\x27,;]{12,}/xi,
  );
  my $bare_phone = qr/(?<![\d.,])3(?:0\d|1\d|2[0-4]|5[01]|99)\d{7}(?!\d)/;
  my $current = "";
  my %excepted;   # id de hallazgo => líneas excluidas
  my $games_metrics = qr/^(?:[\w.-]+\s+\|\s)?GameEventDTO\(sessionToken=[^\s,]*, userHash=[^\s,]*, isBrandedMode=(?:true|false|null), campaignId=[^\s,]*, gameTitle=.*, payload=.*, technicalData=.*\)\s*$/;
  while (<>) {
    if ($ARGV ne $current) { $current = $ARGV; }
    my $line = $_;
    if ($line =~ $games_metrics) {
      # F-GAMES-METRICS-STDOUT: se borran solo los dos valores de sesión; el resto se sigue revisando.
      $line =~ s/GameEventDTO\(sessionToken=[^\s,]*, userHash=[^\s,]*/GameEventDTO(sessionToken=-, userHash=-/;
      $excepted{"F-GAMES-METRICS-STDOUT"}++;
    }
    my @types;
    for my $name (sort keys %rules) { push @types, $name if $line =~ $rules{$name}; }
    push @types, "celular" if $line =~ $kw_phone && $line =~ $bare_phone && !grep { $_ eq "celular" } @types;
    for my $t (@types) { printf "%s:%d: %s\n", ($ARGV eq "-" ? "<stdin>" : $ARGV), $., $t; $found++; }
    close ARGV if eof;
  }
  printf STDERR "check-pii: excepcion %s (hallazgo conocido): %d linea(s) excluida(s)\n", $_, $excepted{$_} for sort keys %excepted;
  print STDERR ($found ? "check-pii: $found hallazgo(s)\n" : "check-pii: limpio\n");
  exit($found ? 1 : 0);
' "${files[@]}"
