# Guía local · prueba de carga en el Mac con Docker (fase local)

Esta guía levanta la API, un MySQL configurado como DigitalOcean, los simuladores de terceros y k6 en el Mac,
siembra 1.000 (A) o 10.000 (B) usuarios, corre la prueba y deja las métricas de la BD y la API y la CPU de k6.
**No usa credenciales de AWS ni de DigitalOcean** y **no toca beta, producción ni el `.env` de la raíz**.
Los resultados son **preliminares** (informe, sección 2): los finales salen de la fase nube.

Todos los comandos se corren desde la **raíz del repositorio**. El atajo `stress-tests/scripts/lt.sh` es el
`docker compose` del proyecto con el archivo de plan elegido en `PLAN` (funciona en bash y en zsh):

```bash
export PLAN=do-1vcpu-1gb          # elige el archivo de local/plans/ (paso 2): límites y parámetros de MySQL
export LT_SCENARIO=A              # elige local/scenarios/<A|B>.env: límites de API, k6, pool y Tomcat (paso 2)
LT=stress-tests/scripts/lt.sh     # LT <args de docker compose>
```

## 0. Requisitos

- Mac con Apple Silicon, **enchufado a la corriente**, y sin nada pesado corriendo (navegadores con muchas pestañas, el
  compose de desarrollo de la raíz, IDE indexando). El compose de la prueba usa su propio proyecto
  (`verygana-loadtest`) y no choca con el de desarrollo, pero le quita memoria.
- **Docker Desktop**: Settings > Resources con **9 CPU y 13 GB de memoria** (la guía y la tabla de límites de
  los archivos de plan parten de esos números; sube a 13 GB **antes de sembrar o correr B**). Con ~8 GB, A con el plan chico y
  el smoke caben; B no, y el informe tiene que anotar cuánto tenía Docker.
- Disco libre: ~15 GB (imagen de la API 1,3 GB, MySQL con B ~1 GB, imágenes de los simuladores).
- Mantener el Mac despierto durante las corridas, en otra terminal: `caffeinate -dimsu`.
- **No correr entre las 22:30 y las 00:00 de Bogotá**: a las 23:00 corre el job de payouts (R9).
- No usar el `.env` de la raíz ni exportar secretos reales: el compose solo lee `stress-tests/env/loadtest.local.env`
  y el guardián de la API (`LoadTestSafetyGuard`) rechaza el arranque si ve una URL o credencial real.
- Docker Compose v2 (`docker compose version`). Los puertos de los simuladores **no** se publican al Mac (la red es
  `internal`, sin salida a Internet): todo se hace por `lt.sh exec/run`.

## 1. Variables de entorno

```bash
cp stress-tests/env/loadtest.local.env.example stress-tests/env/loadtest.local.env
```

El archivo es ficticio y desechable (git lo ignora). No hay que editarlo salvo `LOADTEST_SEED_USERS` (paso 4).

## 2. Elegir el archivo de plan, la regla de llaves primarias y el trust

Hay **dos ejes** y los dos se cargan en cada `lt.sh`/`run-scenario.sh` (el escenario después del plan, así que manda):

- `PLAN` = `stress-tests/local/plans/<plan>.env`: contenedor `mysql` (CPU, memoria, buffer pool, `max_connections`, llaves
  primarias, trust). La fuente y fecha de esos valores van en su cabecera.
- `LT_SCENARIO` = `stress-tests/local/scenarios/<A|B>.env`: reparto del resto del Mac. `run-scenario.sh <smoke|A|B>` lo
  fija solo con su argumento (smoke usa A); para `up`, `config` y el sembrado exporta `LT_SCENARIO` tú.

| `LT_SCENARIO` | API | Pool / Tomcat | k6 | wiremock + minio |
|---------------|-----|---------------|----|------------------|
| `A` | 2 CPU / 4 GB | 10 / 200 | 1 CPU / 1 GB | 0,5 + 0,5 CPU |
| `B` (límites reales de L-B2 y L-B3) | 3 CPU / 4 GB | 150 / 400 | 3 CPU / 3 GiB | 0,5 + 0,5 CPU |

Con B y k6 en 1 GB el kernel mata a k6 a los 2 minutos (corrida `...-FALLIDA-k6-OOM-1g`): no bajes de 3 GiB. Los límites del
`api` se fijan **al crear** el contenedor: cambia de escenario con `LT_SCENARIO=B $LT up -d` antes de correr. Comprobación
sin levantar nada: `LT_SCENARIO=B $LT --profile tools config | grep -E 'cpus|mem_limit|DB_POOL_SIZE|TOMCAT'`.

Archivos de plan:

| `PLAN` | Plan de DigitalOcean equivalente | Se evalúa en |
|--------|----------------------------------|--------------|
| `do-1vcpu-1gb` | 1 vCPU / 1 GB | A (primero) |
| `do-1vcpu-2gb` | 1 vCPU / 2 GB | A, B |
| `do-2vcpu-4gb` | 2 vCPU / 4 GB | A, B |
| `do-4vcpu-8gb` | 4 vCPU / 8 GB | A; y como plan **holgado** para sembrar B |

- **Regla de llaves primarias.** Para crear el esquema desde una base vacía, `sql_require_primary_key` va **siempre apagado**
  (`MYSQL_REQUIRE_PK=OFF`, como vienen los archivos de plan): V1 crea 8 tablas sin llave y falla con el error 3750 si el
  requisito está encendido. Se enciende **después de migrar**, y solo se puede si la rama trae la migración que les agrega la
  llave: `ls src/main/resources/db/migration/ | grep '^V202610041435__' || echo "no hay llaves primarias"`. Si aparece, tras migrar
  puedes poner `MYSQL_REQUIRE_PK=ON` en el archivo de plan o exportarlo (`export MYSQL_REQUIRE_PK=ON`; una variable del shell pisa
  la del archivo) y recrear el contenedor `mysql`; si no aparece, el requisito sigue apagado en toda la prueba.
- **Trust de los triggers de V9.** Los archivos traen `MYSQL_TRUST_FUNCTION_CREATORS=ON` porque V9 falla con `OFF`
  (error 1419). En DigitalOcean real no es configurable y se resuelve en la nube con D6 (V9 se prueba en un cluster real de DO; si falla, RDS con `log_bin_trust_function_creators=1`); no lo cambies sin saberlo.
- Anota el archivo elegido y la variante de llaves primarias: van al informe.

## 3. Verificar las migraciones

```bash
$LT up -d --wait mysql                                     # espera a que MySQL esté sano (~20 s)
stress-tests/scripts/run-sql.sh check-schema.sql          # variables de MySQL y tablas sin PK (base vacía: ninguna)
```

Con la base vacía el script muestra `sql_require_primary_key`, `log_bin`, `gtid_mode` y
`log_bin_trust_function_creators` con los valores del plan. La verificación real (variante 4 de 3.2.1) es el paso 4:
la **API** migra sola sobre la base vacía. Cuando termine, `check-schema.sql` debe mostrar `trigger_count = 4` y
`SELECT COUNT(*) FROM flyway_schema_history` las migraciones de la rama (V1 a V12, `V202610021500__refresh_token_hash` y `V202610041435__primary_keys_join_tables` hoy: 14 filas):

```bash
stress-tests/scripts/run-sql.sh check-schema.sql | grep -A3 trigger_count
```

Opcional, para probar las migraciones con Flyway CLI sin la API (usa `MYSQL_REQUIRE_PK` y trust del
plan; deja la base migrada): `docker run --rm --network verygana-loadtest_lt-internal -v "$PWD/src/main/resources/db/migration:/flyway/sql:ro" flyway/flyway:11.7.2 -url=jdbc:mysql://mysql:3306/verygana -user=verygana_app -password=lt-app-local migrate`.

## 4. Levantar todo y sembrar

**Escenario A** (1.000 usuarios), con el plan `do-1vcpu-1gb` (u otro de la tabla):

```bash
sed -i.bak 's/^LOADTEST_SEED_USERS=.*/LOADTEST_SEED_USERS=1000/' stress-tests/env/loadtest.local.env && rm stress-tests/env/loadtest.local.env.bak
$LT up -d --build
until $LT exec -T tools curl -sf http://api:8080/actuator/health | grep -q UP; do sleep 5; done; echo "API arriba"
stress-tests/scripts/run-sql.sh check-seed.sql | head -20      # 940 / 50 / 5 / 3 / 2
```

- La **primera** construcción de la imagen de la API tarda ~15 min (Maven sin caché); las siguientes, menos de 1 min.
  Hay que reconstruir (`up -d --build`) cada vez que cambie Java o SQL del sembrado.
- El sembrado de A tarda ~12 s después del arranque. Repetirlo no duplica nada: recrear la API
  (`$LT up -d --force-recreate --no-deps api`) deja `check-seed.sql` idéntico.
- `check-seed.sql` es solo lectura y solo imprime conteos.

**Escenario B** (10.000 usuarios): siembra con el plan **holgado** y sin k6, para no medir el sembrado, y luego cambia al
plan a evaluar **sobre el mismo volumen** (paso 8):

```bash
export PLAN=do-4vcpu-8gb LT_SCENARIO=B
sed -i.bak 's/^LOADTEST_SEED_USERS=.*/LOADTEST_SEED_USERS=10000/' stress-tests/env/loadtest.local.env && rm stress-tests/env/loadtest.local.env.bak
$LT up -d --build      # si A ya estaba sembrada, la API solo agrega consumidores 941+ y comerciales 51+
stress-tests/scripts/run-sql.sh check-seed.sql | head -20      # 9.490 / 500 / 5 / 3 / 2 (~4 min)
```

## 5. Smoke

```bash
$LT run --rm -e SCENARIO=smoke k6 run /scripts/main.js | tee /tmp/smoke.out
```

Esperado: **código de salida 0**, `error=0.00%`, "Endpoints ejercitados (357)", "Con recorrido y sin ejercitar (0)" y la
lista de exclusiones con motivo (admin, utilitario, webhook, streaming). Dura ~40 s. Comprobaciones del ambiente:

```bash
# Simuladores con tráfico (POST a /__admin/requests/count con un filtro; esperado tras un smoke: 11 en total,
# 2 de payouts GET /banks, 4 de ZapSign checks y 1 de POST /docs/)
for q in '{}' '{"method":"GET","urlPath":"/wompi-payouts/v1/banks"}' '{"method":"POST","urlPath":"/zapsign/api/v1/checks/"}' '{"method":"POST","urlPath":"/zapsign/api/v1/docs/"}'; do
  $LT exec -T tools curl -s -X POST -H 'Content-Type: application/json' -d "$q" http://wiremock:8080/__admin/requests/count; echo
done
# Contadores de falsos (el endpoint exige el token de scrapeo; sin él responde 401)
$LT exec -T tools curl -s -H "Authorization: Bearer loadtest-scrape-token" http://api:8080/actuator/prometheus | grep '^loadtest_stub_calls_total'
# Red sin salida a Internet: tiene que FALLAR (exit 28 o 7)
$LT exec -T tools curl -m 5 -sf https://www.google.com -o /dev/null; echo "exit=$?"
```

**No guardes** el diario completo de WireMock (`/__admin/requests`): lleva el cuerpo de las peticiones a ZapSign con el
correo y el teléfono de los usuarios (ficticios, pero `check-pii.sh` los marca). Usa el conteo de arriba.

## 6. Correr A o B y guardar las métricas

Un solo comando hace todo el paso (con la API arriba y sembrada):

```bash
caffeinate -dimsu &                      # o en otra terminal
PLAN=do-1vcpu-1gb stress-tests/scripts/run-scenario.sh A      # ~27 min + preparación
# B (recrea antes el api con los límites de B): LT_SCENARIO=B PLAN=do-2vcpu-4gb stress-tests/scripts/lt.sh up -d
#    PLAN=do-2vcpu-4gb stress-tests/scripts/run-scenario.sh B                   # ~47 min
# Ensayo corto: DURATION_SCALE=0.12 PLAN=... stress-tests/scripts/run-scenario.sh A   (~3,5 min)
```

Antes de la corrida, para ver las etapas y los VUs por rol que va a usar (200 en A: 180/10/5/3/2; 2.000 en B:
1.890/100/5/3/2). **`k6 inspect` no lee el `-e` de Docker**: el escenario va con `--env`:

```bash
$LT run --rm k6 inspect --env SCENARIO=B /scripts/main.js
```

Deja todo en `stress-tests/results/<fecha>-<escenario>-<plan>/` (se ignora por git):

| Archivo | Qué es | Lo genera |
|---------|--------|-----------|
| `summary-<escenario>-*.json`, `k6.out` | Tabla por endpoint (p50/p95/p99/error), no ejercitados y exclusiones | `summary.js` |
| `docker-stats.csv` | CPU, memoria, BlockIO y red de `mysql`, `api`, `k6`, `wiremock`, `minio` cada ~5 s; `cpu_pct_of_limit` es la CPU ÷ el límite del contenedor | `collect-docker-stats.sh` |
| `api-metrics.csv` | Pool de Hikari, hilos de Tomcat, CPU y heap de la JVM, contador de falsos | `collect-api-metrics.sh` |
| `snapshot-before.tsv`, `snapshot-after.tsv`, `snapshot-delta.tsv` | Contadores de MySQL antes y después y sus deltas: consultas/s, IOPS de lectura y escritura, conexiones, colas de brandeo y mascotas | `snapshot.sql`, `snapshot-delta.sh` |
| `top-digests.txt` | Top 25 de consultas por tiempo total y por promedio, y las lentas (solo `DIGEST_TEXT`, sin literales) | `top-digests.sql` |
| `table-sizes.txt` | Tamaño (datos + índices) por tabla, después de `ANALYZE` | `table-sizes.sql`, `analyze-tables.sql` |
| `tls-check.txt` | Las conexiones de la app van cifradas (`sin_tls = 0`) | `tls-check.sql` |
| `db-latency.txt` | Línea base de RTT hacia MySQL (en local casi nulo) y cifrado usado | `db-latency.sh` |
| `check-seed-before.txt`, `check-seed-after.txt` | Conteos antes y después (la deriva entre corridas) | `check-seed.sql` |
| `api.log` | Log de la API durante la corrida | `lt logs --since` |
| `check-pii.txt`, `check-pii-exit-code.txt` | Verificación de datos personales y secretos sobre toda la carpeta | `check-pii.sh` |
| `effective-config.txt`, `running-limits.txt` | Valores **efectivos**: lo que compose resuelve (plan + escenario + variables del shell: CPU, memoria, pool, Tomcat, parámetros de MySQL) y los límites de los contenedores en marcha; avisa si el `api` no coincide con el escenario | `run-scenario.sh` |
| `plan.env`, `scenario.env`, `start-utc.txt`, `k6-exit-code.txt` | Los archivos de plan y de escenario tal cual (pueden estar pisados por variables del shell: manda `effective-config.txt`), inicio y código de k6 | `run-scenario.sh` |

Qué mide cada script de `stress-tests/scripts/` por separado (se pueden correr a mano con el mismo nombre):

- `run-sql.sh <archivo.sql> [salida]`: corre un archivo de `stress-tests/db/` como `verygana_monitor` (otro usuario con
  `MYSQL_USER` y `MYSQL_PASSWORD`).
- `collect-docker-stats.sh <csv> [intervalo=5] [duración=0]`, `collect-api-metrics.sh <csv> [intervalo=15] [duración=0]`
  (duración 0 = una sola lectura en la API, hasta Ctrl-C en `docker stats`).
- `snapshot-delta.sh <antes> <después>`, `db-latency.sh [N] [repeticiones]`, `plateau-stats.sh <carpeta> <A|B>`.
- `check-pii.sh <archivo|carpeta>`: sale con 0 si no hay correos, celulares, cédulas, cuentas ni tokens; con 1 y la
  lista `archivo:línea: tipo` si los hay (nunca imprime el dato). Se prueba contra `scripts/testdata/pii-positive.txt`,
  que **tiene** que fallar.

Hallazgo conocido, con **excepción nominal** en `check-pii.sh` (id `F-GAMES-METRICS-STDOUT`):
`GameController` escribe con `System.out.println` el `sessionToken` y el `userHash` de
cada `POST /games/metrics`; está pendiente de arreglo. El script ignora **solo** la línea completa con el `toString()` de
`GameEventDTO` (con o sin el prefijo `api-1  | ` de compose), borra de ella esos dos valores y sigue revisando el resto
de la línea. Al final imprime `excepcion F-GAMES-METRICS-STDOUT ...: N linea(s) excluida(s)`; ese N va al informe como
hallazgo. Un token en cualquier otra línea sí se marca. Se prueba con `bash scripts/test-check-pii.sh`
(`testdata/pii-exception-ok.txt` sale con 0 y `pii-exception-leak.txt` con 1).

## 7. Leer el resultado: árbol de diagnóstico y marca de "sesgada"

```bash
stress-tests/scripts/plateau-stats.sh stress-tests/results/<carpeta> A     # o B
```

Entrega, para la **meseta** (A: minutos 5-25; B: minutos 24-44, los 20 min a 2.000 VUs), la CPU y memoria de cada
contenedor, las métricas de la API y el veredicto de los umbrales y de "sesgada". Con `DURATION_SCALE` en la corrida, páselo
igual a `plateau-stats.sh`. Fuentes locales:

| Pregunta del árbol | Dónde se lee |
|-------------------------------|--------------|
| 1. ¿La API es el cuello? CPU de la API > 80 % o Tomcat al máximo | `api-metrics.csv` (`process_cpu_usage`, `tomcat_threads_busy_threads` vs `tomcat_threads_config_max_threads`); CPU del contenedor `api` en `docker-stats.csv` |
| 2. ¿El pool es chico? `hikaricp_connections_pending` > 0 con CPU de BD < 60 % y conexiones < máximo | `api-metrics.csv` y `snapshot-after.tsv` (`Threads_connected`, `max_connections`) |
| 3. ¿El plan de BD no aguanta? CPU ≥ 75 % de promedio, conexiones ≈ máximo o p95 > 1 s con la BD saturada | `docker-stats.csv` (`cpu_pct_of_limit` de `mysql`), `snapshot-delta.tsv`, `top-digests.txt` |
| 4. ¿Cumple los umbrales? p95 < 1 s, error < 1 %, CPU BD < 75 %, sin agotar conexiones | `plateau-stats.sh` y el resumen de k6 |

Si el cuello es la API (1) se anota y se deja para la nube (R3); si el pool (2), sube `DB_POOL_SIZE` en el archivo de
escenario (`local/scenarios/`) dentro del límite (`0,8 × max_connections`) y repite; si el plan de BD (3), pasa al siguiente (paso 8).

**Una corrida queda "sesgada"** (se marca en el informe, no se descarta) si se cumple **cualquiera**:

- el contenedor `k6` pasa del 70 % de su límite de CPU en promedio durante la meseta;
- el contenedor `k6` pasa del 90 % de su límite de **memoria** en promedio durante la meseta (con el tope encima k6 se
  estrangula y la carga ya no es la de la etapa; `plateau-stats.sh` lo marca);
- la suma de CPU de todos los contenedores pasa del 85 % de las CPU de Docker Desktop;
- k6 reporta `dropped_iterations` o no llega al número de VUs de la etapa.

Sesgos que el informe declara **siempre**: núcleos M4 más rápidos que una vCPU de DO, disco NVMe frente a disco de red,
RTT casi nulo (ver `db-latency.txt`) y k6 compartiendo máquina. En B, si k6 se queda sin memoria, el dato de B pasa
a la nube.

## 8. Subir de plan sin volver a sembrar

El volumen de MySQL (`verygana-loadtest_mysql-data`) se conserva; solo cambia el contenedor:

```bash
export PLAN=do-2vcpu-4gb          # el siguiente de la escalera (LT_SCENARIO se mantiene)
$LT up -d                         # recrea mysql y api (cambian sus límites, buffer pool y pool de conexiones)
until $LT exec -T tools curl -sf http://api:8080/actuator/health | grep -q UP; do sleep 5; done
stress-tests/scripts/run-sql.sh check-seed.sql | head -20     # los mismos conteos: no se resembró
```

Si cambias la regla de llaves primarias o el trust entre planes, es otra base: `down -v` y vuelve al paso 4.

## 9. Limpiar

```bash
$LT down -v                                   # contenedores, red y volúmenes (se pierde la base sembrada)
docker image rm verygana-loadtest-api         # opcional: libera 1,3 GB
rm stress-tests/env/loadtest.local.env        # opcional
```

`stress-tests/results/` no se borra: son las salidas de las corridas.

## Si algo falla

- La API no arranca y el log dice `LoadTestSafetyGuard`: algo apunta a un proveedor real o el perfil trae `beta`/`dev`.
  Es lo esperado con `SPRING_PROFILES_ACTIVE=prod,beta,loadtest`; no hay que "arreglarlo".
- `/actuator/prometheus` responde 401: falta `Authorization: Bearer <METRICS_SCRAPE_TOKEN>` (el de `loadtest.local.env`).
- Docker Desktop se reinició durante la corrida (los contenedores `mysql`/`api` quedan `Exited`): la corrida no vale. Sube
  los contenedores con `$LT up -d` (los datos persisten) y repítela.
- `snapshot-delta.sh` dice "Uptime no avanzó": MySQL se reinició entre los dos snapshots; repite la corrida.
- k6 sale con código 99: se incumplió un threshold (p95 o error); no es un fallo del montaje.
