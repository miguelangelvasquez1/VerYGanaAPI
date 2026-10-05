#!/usr/bin/env bash
# Prueba de la excepcion nominal de check-pii.sh. Uso: bash test-check-pii.sh (sale con 0 si todo se cumple).
cd "$(dirname "${BASH_SOURCE[0]}")" || exit 2
fail=0
out=$(bash check-pii.sh testdata/pii-exception-ok.txt 2>&1); rc=$?
echo "ok.txt   exit=${rc} (esperado 0)"; echo "${out}" | sed 's/^/   /'
[ "${rc}" -eq 0 ] && echo "${out}" | grep -q "3 linea(s) excluida(s)" || fail=1
out=$(bash check-pii.sh testdata/pii-exception-leak.txt 2>&1); rc=$?
echo "leak.txt exit=${rc} (esperado 1, con hallazgo en las lineas 3 a 6; la 2 queda excluida)"; echo "${out}" | sed 's/^/   /'
for n in 3 4 5 6; do echo "${out}" | grep -q "pii-exception-leak.txt:${n}:" || { echo "   FALTA hallazgo en linea ${n}"; fail=1; }; done
[ "${rc}" -eq 1 ] || fail=1
out=$(bash check-pii.sh testdata/pii-negative.txt 2>&1); rc=$?
echo "negative.txt exit=${rc} (esperado 0, incluye UUID con forma de celular)"; [ "${rc}" -eq 0 ] || { echo "${out}" | sed 's/^/   /'; fail=1; }
out=$(bash check-pii.sh testdata/pii-positive.txt 2>&1); rc=$?
echo "positive.txt exit=${rc} (esperado 1, con hallazgo de celular en 3 lineas)"
[ "${rc}" -eq 1 ] || fail=1
n=$(echo "${out}" | grep -c ': celular$'); echo "   celulares marcados: ${n} (esperado 3)"; [ "${n}" -eq 3 ] || fail=1
[ "${fail}" -eq 0 ] && echo "TEST OK" || echo "TEST FALLA"
exit "${fail}"
