<!--
Plantilla del informe de la prueba de carga. Se copia a `results/<fecha>-informe.md` y se llena;
el informe lleno va a Notion, esta plantilla se queda en blanco en el repo.
Convención: `…` = celda por llenar. "fuente" dice de dónde sale el dato. Ninguna celda lleva
correos, teléfonos, cédulas ni datos bancarios; `check-pii.sh` se corre sobre todo lo que se pegue.
Las secciones 2 (local) y 3 (nube) tienen la MISMA estructura a propósito: cada métrica, cada
umbral de aceptación y cada dato de costo tiene su celda en las dos.
-->

# Informe · Dimensionamiento de la base de datos para 1.000 y 10.000 usuarios

| Dato | Valor |
|------|-------|
| Fecha del informe | … |
| Autor | … |
| Commit / rama de la API medida | … |
| Estado | Fase local: **preliminar** · Fase nube: … (final / pendiente) |

## 0. Resumen para quien decide el presupuesto

| Escenario | Instancia mínima (local, **preliminar**) | Instancia mínima (nube, **final**) | Costo mensual DO (1 nodo / con standby) | Equivalente RDS (mensual) |
|-----------|------------------------------------------|------------------------------------|------------------------------------------|---------------------------|
| A · 1.000 usuarios (200 VUs) | … (o "≥ plan X, a confirmar en la nube") | … (pendiente hasta la fase nube) | … / … | … |
| B · 10.000 usuarios (2.000 VUs) | … (o "≥ 4 vCPU / 8 GB, a confirmar en la nube") | … (pendiente hasta la fase nube) | … / … | … |

Una línea de conclusión: …

---

## 1. Comunes a las dos fases

### 1.1 Supuestos de la prueba

| Supuesto | Valor usado | Fuente |
|----------|-------------|--------|
| Concurrencia en la hora pico | 20 % de los usuarios registrados: 200 VUs en A, 2.000 en B | supuesto de negocio |
| Personal interno | Fijo en A y B: 5 diseñadores, 3 admins, 2 de cumplimiento, **todos activos** en la hora pico (un VU por usuario, sin repetir) | `LoadTestSeedPlan` |
| Resto de usuarios | 95 % consumidores / 5 % comerciales (redondeo half-up) | `LoadTestSeedPlan` |
| Usuarios sembrados A | 940 consumidores / 50 comerciales / 5 / 3 / 2 | `check-seed.sql` |
| Usuarios sembrados B | 9.490 / 500 / 5 / 3 / 2 | `check-seed.sql` |
| VUs en A | 180 / 10 / 5 / 3 / 2 | `k6 inspect --env SCENARIO=A` |
| VUs en B | 1.890 / 100 / 5 / 3 / 2 (escalones de consumidores 465 / 940 / 1.415 / 1.890 y de comerciales 25 / 50 / 75 / 100, personal fijo) | `k6 inspect --env SCENARIO=B` |
| Planes de los comerciales | 40 % BASIC / 40 % STANDARD / 20 % PREMIUM | `check-seed.sql` |
| Pausas | 3 a 10 s entre pasos de un recorrido; 30 a 60 s entre sesiones | `lib/config.js` |
| Mezcla de sesiones de consumidor | C1 anuncios 35 %, C2 juegos 20 %, C3 rifas 10 %, C4 mascotas 10 %, C5 marketplace 10 %, C6 encuestas 8 %, C7 perfil/referidos/PQRS 5 %, C8 registro 2 % | `consumer.js` |
| Autenticación | Login una vez por VU y renovación con `/auth/refresh` (no por iteración) | `lib/auth.js` |
| Duración A | 27 min: subida 5 min, meseta 20 min, bajada 2 min | decisión del equipo |
| Duración B | ~47 min: escalones 500 / 1.000 / 1.500 / 2.000 (3 + 3 min cada uno), 20 min a 2.000, bajada 3 min | decisión del equipo |
| Historial sembrado | Repartido en los 90 días anteriores. Multiplicadores por usuario: ver tabla siguiente | `LoadTestSeedPlan` |
| Terceros | Todos simulados (WireMock, MinIO, SMS y correo falsos con latencia de 200 y 150 ms, reCAPTCHA falso). Sí se ejercitan: ZapSign, payouts de Wompi (`GET /banks`), MinIO. **No** se ejercitan: Wompi pagos (el checkout no sale al proveedor), Random.org (el sorteo es de admin), webhook de ZapSign | recorridos de k6 |
| Jobs `@Scheduled` | Se dejaron correr (liquidación cada 10 min, expiración cada 30 min); no se corrió entre 22:30 y 00:00 de Bogotá (R9) | plan R9 |
| Deriva entre corridas | Las escrituras de una corrida engordan la base de la siguiente; se registra el conteo antes y después | `snapshot.sql` |

Multiplicadores del historial usados (completar con los valores reales de `LoadTestSeedPlan` si cambiaron):

| Por consumidor | Filas | Por comercial | Filas |
|----------------|-------|---------------|-------|
| `ad_watch_session` | … (60) | `ads` + `ad_assets` + `target_audiences` | … (6) |
| `ad_likes` | … (50) | `campaigns` | … (2) |
| `key_transactions` | … (80) | `surveys` (5 preguntas × 4 opciones) | … (2) |
| `xp_key_transaction_log` | … (40) | `products` / `product_stock` | … (8 / 25 c.u.) |
| `game_sessions` / `game_session_metrics` | … (20 / 60) | `budget_transactions` | … (40) |
| `raffle_tickets` / `raffle_participations` | … (15 / 5) | `branding_requests` | … (2) |
| `notifications` | … (40) | demás tablas | ver `LoadTestSeedPlan` |
| `purchases` / `purchase_items` | … (2 / 3) | | |
| demás tablas | ver `LoadTestSeedPlan` | | |

Filas totales sembradas: A … · B … (fuente: `check-seed.sql`, `table-sizes.sql`).

### 1.2 Cómo se decide si una instancia "aguanta"

Una instancia aguanta un escenario si, con la carga sostenida (meseta), se cumplen **las cuatro**:

1. p95 global < 1 s (`http_req_duration{expected_response:true}`).
2. Tasa de error < 1 %.
3. CPU de la base < 75 % de promedio en la meseta.
4. Sin agotar conexiones (`Threads_connected` < `max_connections`, `Connection_errors_max_connections` = 0, `hikaricp_connections_pending` sin acumularse).

Definición de **error**: respuestas 5xx, timeouts y 4xx no esperados. Los 4xx de negocio que el
recorrido provoca a propósito (cupos del plan, cooldown de anuncios, camino de rechazo de códigos por correo)
se reportan aparte como "rechazos esperados" y **no** cuentan. Un 500 **siempre** cuenta como error en A y B:
solo el `smoke` tolera algunos 500 conocidos (sección 1.6) y el smoke no se evalúa contra los umbrales.

### 1.3 Tabla de costos de referencia

Precios de lista aproximados en USD por mes, **a confirmar el día de la prueba** (fecha de consulta: …).
La misma tabla sirve a las dos fases; en la local la fila marcada es **preliminar**.

| Plan DO (vCPU / RAM / disco) | Precio DO (1 nodo) | + nodo standby | Equivalente RDS MySQL (Single-AZ, on-demand) | RDS + 20 GB gp3 | Pasó A | Pasó B |
|------------------------------|--------------------|----------------|----------------------------------------------|-----------------|--------|--------|
| 1 / 1 GB / 10 GB | ~15 | +15 | db.t4g.micro | ~14 | … | … |
| 1 / 2 GB / 25 GB | ~30 | +30 | db.t4g.small | ~26 | … | … |
| 2 / 4 GB / 38 GB | ~60 | +60 | db.t4g.medium | ~50 | … | … |
| 4 / 8 GB / 115 GB | ~120 | +120 | db.t4g.large / db.m7g.large | ~97 / ~126 | … | … |
| 6-8 / 16 GB | ~240 | +240 | db.m7g.xlarge | ~250 | … | … |

Los t4g de RDS funcionan con créditos de CPU y no son directamente comparables con la CPU compartida de DO.
Costo mensual de la instancia mínima (plan + almacenamiento + nodos de respaldo si aplica):

| Escenario | Instancia mínima | Plan DO | Almacenamiento (datos + índices + holgura) | Nodo standby | **Total DO/mes** | RDS equivalente/mes |
|-----------|------------------|---------|---------------------------------------------|--------------|------------------|---------------------|
| A | … | … | … | … | … | … |
| B | … | … | … | … | … | … |

### 1.4 Costo de haber corrido la prueba

| Fase | Recursos | Horas | USD |
|------|----------|-------|-----|
| Local | Mac del equipo (sin recursos de pago) | … h de máquina | 0 |
| Nube | EC2 API, EC2 generador, EBS, IPv4, transferencia, DO MySQL (incluido el cluster de verificación) | … | … (estimado en la guía: 5-8; alerta de AWS Budgets en 20) |

### 1.5 Pruebas que no se hicieron (alcance)

Nada contra beta ni producción; sin llamadas reales a Wompi, ZapSign, Twilio, correo o R2; sin medir el front, el
build de Unity ni el egress de archivos; no se optimizó ninguna consulta ni índice: el informe señala cuellos,
arreglarlos es otra spec.

### 1.6 Hallazgos (bugs conocidos que la prueba toca y que NO se arreglan aquí)

Los contadores salen del resumen de k6 (columna "Rechazos" y errores del endpoint) y del smoke. Cada fila
se arregla en su propia spec.

| # | Hallazgo | Endpoint(s) | Efecto en la medición | Observado en la corrida (llamadas / 5xx) |
|---|----------|-------------|-----------------------|-------------------------------------------|
| H1 | Encuestas sembradas con `target_gender = 'ALL'` no se ven: la consulta compara el género del consumidor con el de la audiencia | `GET /surveys` | Lista vacía para todo consumidor con género; C6 abre encuestas por id (`SURVEY_IDS`), así que `start` y `submit` sí se miden | … |
| H2 | 500 en premios sin imagen | `GET /winners/my-prizes` | 5xx si un premio ganado no tiene imagen; el sembrado ya trae imágenes | … |
| H3 | Corregido (spec 008): la reserva sin fondos respondía 500 por un `IllegalStateException` sin mapear en `TreasuryServiceImpl`; ahora responde 409 con mensaje de negocio | `POST /consumer/wallet/keys/spend` | 409 (rechazo) si `KEYS_RESERVE` no está fondeada; el sembrado la fondea (script 07) | … |
| H4 | `IllegalStateException` sin mapear → 500 en `PlanServiceImpl` (estado del plan que no aplica) | `POST /plans/checkout`, `POST /plans/recharge/request`, `POST /plans/recharge/{contractId}/checkout`, `POST /plans/change-request/{id}/top-up-checkout` | Cuentan como error en A y B; solo el smoke los tolera | … |
| H5 | Tipo de parámetro inválido → 500 | `GET /admin/audit-logs`, `GET /admin/security-events`, `PUT /game-designer/pet/notifications/{id}`, `DELETE /game-designer/pet/notifications/{id}` | Las lecturas de admin van con parámetros válidos; las de notificaciones usan una PK numérica inexistente (rechazo) | … |
| H6 | `PetNotificationResponseDTO.id` expone el `externalId` (texto) y PUT/DELETE reciben la PK numérica: el diseñador no puede editar ni borrar un aviso que creó | `GET/POST /game-designer/pet/notifications`, `PUT/DELETE /game-designer/pet/notifications/{id}` | Esos dos pasos se miden por el camino de rechazo (404) | … |
| H7 | Devuelven la entidad JPA `User` (viola la constitución §2) | `GET /users/email/{email}`, `GET /users/public-id/{publicId}` | Ninguno de rendimiento; riesgo de PII y de acoplamiento | … |
| H8 | `/users/exists/{email}` y `/users/exists/phoneNumber/{phone}` llevan PII en la ruta | `GET /users/exists/*` | La URL se saca de las métricas de k6; se prueban con datos ficticios | … |
| H9 | `GET /adLike/{adId}/likes` es solo del dueño del anuncio | `GET /adLike/{adId}/likes` | Se llama desde el comercial (recorrido M); desde un consumidor siempre da 404 | … |
| H10 | `WompiClient.generateIntegrityHash` escribe el `integritySecret` en INFO | checkout de Wompi | En la prueba es un secreto falso y el nivel es WARN; en prod y beta filtra el real | n/a |
| H11 | Hay ~59 sentencias de log con correo, teléfono o identificador; `/games/metrics` registra credenciales de sesión (pendiente de arreglo) | varios | En loadtest `com.verygana2` va en WARN; `check-pii.sh` lo vigila | … |
| H12 | Faltan timeouts en los `WebClient` de Wompi y ZapSign y en el `RestClient` de reCAPTCHA; sus `catch (Exception)` juntan "inválido" con "no contesta" (R6) | proveedores | Los simuladores tienen recursos de sobra y se vigilaron | n/a |
| H13 | Nuevos de esta corrida | … | … | … |

---

## 2. Fase local (PRELIMINAR)

> **Estos números son preliminares.** Se midieron en un Mac con la API, MySQL, k6 y los simuladores compartiendo
> máquina, con RTT nulo y disco NVMe. Sirven para depurar y para dar una primera estimación; los números
> **finales** de costo salen de la fase nube. Toda cifra de esta sección que se cite fuera del
> informe debe llevar la palabra "preliminar".

### 2.1 Máquina y reparto de Docker Desktop

| Dato | Valor |
|------|-------|
| Equipo | Apple M4, 10 núcleos (4 rendimiento + 6 eficiencia), … GB |
| Docker Desktop | … CPU y … GB (la guía pide 9 CPU y 13 GB; si hubo menos, decir cuánto y qué corrida afectó) |
| Mac enchufado y `caffeinate -dimsu` | sí / no |
| Hora de las corridas (no entre 22:30 y 00:00 de Bogotá) | … |
| Versión de MySQL de la imagen | … |
| Commit de la API | … |

| Contenedor | A (CPU / RAM) | B (CPU / RAM) |
|------------|---------------|---------------|
| `mysql` | el plan evaluado (2.4) | ídem |
| `api` (Tomcat, `-XX:MaxRAMPercentage`) | … | … |
| `k6` | … | … |
| `wiremock` + `minio` | … | … |
| Suma de CPU (debe ser ≤ CPU de Docker Desktop) | … | … |

### 2.2 ¿las migraciones pasan como en DO?

| Variante | `sql_require_primary_key` | `log_bin_trust_function_creators` | Resultado | Dónde falla |
|-----------------------|---------------------------|-----------------------------------|-----------|-------------|
| 1 | ON | OFF | … | … (tabla sin PK) |
| 2 | OFF (regla de llaves primarias) | OFF | … | … (`CREATE TRIGGER` de V9, error 1419) |
| 3 | OFF | ON | … | — |
| 4 · la API arranca sobre base vacía y migra sola | … | … | … | … |

- ¿La rama medida trae `V202610041435__primary_keys_join_tables.sql`? … → `sql_require_primary_key` al crear el esquema (siempre OFF) y después de migrar: …
- ¿Se siguió con `MYSQL_TRUST_FUNCTION_CREATORS=ON`? … → **la decisión sobre los triggers se escala con este dato**: …
- Triggers del esquema (`check-schema.sql`): … (esperado 4).

### 2.3 Resultado del smoke y del sembrado

| Comprobación | Resultado |
|--------------|-----------|
| Smoke de los 5 roles: peticiones / p95 / error | … / … / … |
| Endpoints con recorrido ejercitados | … de 356 (esperado: todos) |
| `check-seed.sql` A (940/50/5/3/2) y B (9.490/500/5/3/2) | … |
| Sembrado idempotente (dos corridas iguales) | sí / no |
| Tiempo de sembrado A / B (desde cero y por crecimiento) | … / … |
| Red sin salida a Internet (capa 4) | confirmada / no |
| `loadtest_stub_calls_total` (email, sms, recaptcha) | … |
| Llamadas a WireMock: ZapSign / Wompi payouts | … / … |
| `check-pii.sh` sobre logs y salidas | limpio / hallazgos: … ; líneas excluidas por la excepción `F-GAMES-METRICS-STDOUT` (`System.out` de `/games/metrics`): … |

### 2.4 Corridas

Una fila por corrida. "Plan local" es el archivo de `local/plans/`; "Plan DO" es su traducción (6.4 del plan).

| Id | Escenario | Plan local (archivo) | Plan DO equivalente | API (CPU/RAM, pool, Tomcat) | Fecha y hora | Duración | k6 salió con código | ¿Aguanta los umbrales? | ¿Sesgada? |
|----|-----------|----------------------|---------------------|-----------------------------|--------------|----------|---------------------|------------------|-----------|
| L-A1 | A | … | … | … | … | … | … | … | … |
| L-A2 | A | … | … | … | … | … | … | … | … |
| L-B1 | B | … | … | … | … | … | … | … | … |
| … | | | | | | | | | |

### 2.5 Métricas por corrida y por plan

**Base de datos** (fuentes locales: `collect-docker-stats.sh`, `snapshot.sql` + `snapshot-delta.sh`, `hikaricp_*` de `collect-api-metrics.sh`, `top-digests.sql`, `table-sizes.sql`). Valores de la **meseta**.

| Métrica | Fuente local | L-A1 | L-A2 | L-B1 | … |
|-----------------|--------------|------|------|------|---|
| CPU de la BD, promedio (CPU % del contenedor ÷ `MYSQL_CPUS`) | `docker stats` | … | … | … | … |
| CPU de la BD, pico | `docker stats` | … | … | … | … |
| Memoria de la BD (MiB usados / límite) | `docker stats` | … | … | … | … |
| Conexiones activas (`Threads_connected`, `Threads_running`) | `snapshot.sql` | … | … | … | … |
| Conexiones máximas usadas / `max_connections` | `snapshot.sql` | … | … | … | … |
| Conexiones rechazadas por el máximo (`Connection_errors_max_connections`) | `snapshot.sql` | … | … | … | … |
| IOPS de lectura (Δ `Innodb_data_reads` ÷ s) | `snapshot-delta.sh` | … | … | … | … |
| IOPS de escritura (Δ `Innodb_data_writes` ÷ s) | `snapshot-delta.sh` | … | … | … | … |
| BlockIO de contraste (MB leídos / escritos) | `docker stats` | … | … | … | … |
| Consultas por segundo (Δ `Questions` ÷ s) | `snapshot-delta.sh` | … | … | … | … |
| Consultas lentas (promedio > 100 ms o máx > 1 s: cuántas) | `top-digests.sql` | … | … | … | … |
| Fallos del buffer pool (`buffer_pool_miss_ratio`) | `snapshot-delta.sh` | … | … | … | … |
| Tamaño en disco (datos + índices) | `table-sizes.sql` | … | … | … | … |

**API** (de `summary.js` y de `collect-api-metrics.sh`).

| Métrica | L-A1 | L-A2 | L-B1 | … |
|---------|------|------|------|---|
| Peticiones por segundo totales | … | … | … | … |
| Latencia p50 / p95 / p99 globales (ms) | … | … | … | … |
| Tasa de error global | … | … | … | … |
| Rechazos esperados (4xx de negocio) | … | … | … | … |
| `dropped_iterations` | … | … | … | … |
| CPU de la API, promedio / pico | … | … | … | … |
| Hilos de Tomcat ocupados / máximo (`tomcat_threads_busy`, `tomcat_threads_config_max`) | … | … | … | … |
| Pool de Hikari: activas / pendientes / máx (`hikaricp_connections_*`) | … | … | … | … |
| Heap de la JVM usado / máx | … | … | … | … |

La tabla **por endpoint** (p50 / p95 / p99 / error por cada endpoint ejercitado) está en 2.8.

### 2.6 Umbrales de aceptación por corrida

| Id | p95 global < 1 s | Error < 1 % | CPU BD < 75 % (promedio) | Sin agotar conexiones | **¿Aguanta?** |
|----|------------------|-------------|---------------------------|------------------------|----------------|
| L-A1 | … | … | … | … | … |
| L-A2 | … | … | … | … | … |
| L-B1 | … | … | … | … | … |

### 2.7 CPU de k6 y marca de "sesgada"

Una corrida queda **sesgada** (se marca, no se descarta) si se cumple cualquiera de las tres.

| Id | CPU de k6, promedio en la meseta (% de su límite) | ¿> 70 %? | Suma de CPU de todos los contenedores (% de las CPU de Docker) | ¿> 85 %? | `dropped_iterations` / VUs alcanzados | ¿Falló? | **Marca** |
|----|---------------------------------------------------|----------|-----------------------------------------------------------------|----------|----------------------------------------|----------|-----------|
| L-A1 | … | … | … | … | … | … | sesgada / limpia |
| L-A2 | … | … | … | … | … | … | … |
| L-B1 | … | … | … | … | … | … | … |

Si k6 se quedó sin memoria o sin CPU en B, el dato de B pasa a la nube: …

### 2.8 Tabla por endpoint y exclusiones

Pegar la salida de `handleSummary` de la corrida aceptada de A y la de B (una fila por endpoint, solo nombres
de plantilla, nunca URLs con datos).

| Recorrido | Endpoint | Llamadas | p50 (ms) | p95 (ms) | p99 (ms) | Error | Rechazos esperados |
|-----------|----------|----------|----------|----------|----------|-------|--------------------|
| … | … | … | … | … | … | … | … |

- Endpoints con recorrido que **no** se ejercitaron en la corrida: … (con la razón).
- Exclusiones con motivo (de `inventory/endpoints.csv`): admin 68, utilitario 8, webhook 3, streaming 1 (confirmar con el CSV vigente).
- Los 10 endpoints más lentos (p95) de B: … y los 5 con más tráfico: …

### 2.9 Árbol de diagnóstico y cuello de botella

| Id | 1. ¿CPU de la API > 80 % o Tomcat al máximo? | 2. ¿`hikaricp_connections_pending` > 0 con CPU BD < 60 %? | 3. ¿CPU BD ≥ 75 % / conexiones ≈ máx / p95 > 1 s con la BD saturada? | 4. ¿Cumple los umbrales? | **Cuello** y acción tomada |
|----|----------------------------------------------|-----------------------------------------------------------|----------------------------------------------------------------------|-------------------|-----------------------------|
| L-A1 | … | … | … | … | … |
| L-B1 | … | … | … | … | … |

Narrativa del cuello (qué se saturó primero y con qué evidencia, qué se cambió entre corridas): …

### 2.10 Instancia mínima **preliminar** y costo

| Escenario | Fila de la escalera que pasó los umbrales | Plan DO equivalente | RDS equivalente | Costo DO/mes (1 nodo / con standby) | A confirmar en la nube |
|-----------|-------------------------------------|----------------------|-----------------|--------------------------------------|-------------------------|
| A | … | … | … | … | sí |
| B | … (o "≥ 4 vCPU / 8 GB, a confirmar en la nube") | … | … | … | sí |

Margen sobre el umbral en la corrida aceptada (CPU BD promedio vs 75 %, p95 vs 1 s): …
Si la corrida aceptada estuvo marcada como sesgada, decirlo aquí: …

### 2.11 Consultas lentas (top de `top-digests.sql`)

Solo `DIGEST_TEXT` normalizado (sin literales). Los 10 primeros por tiempo total y los de la sección "lentas", por corrida.

| Corrida | Llamadas | Total (ms) | Promedio (ms) | Máx (ms) | Filas examinadas | Sin índice | `DIGEST_TEXT` |
|---------|----------|------------|---------------|----------|------------------|-----------|----------------|
| … | … | … | … | … | … | … | … |

### 2.12 Tamaño en disco por tabla y colas

Top 15 de `table-sizes.sql` tras `ANALYZE TABLE`, en MB.

| Tabla | Filas estimadas A | Datos + índices A (MB) | Filas estimadas B | Datos + índices B (MB) | Factor B/A |
|-------|-------------------|-------------------------|-------------------|-------------------------|------------|
| … | … | … | … | … | … |
| **Total del esquema** | | … | | … | … |

Almacenamiento a contratar (total B + crecimiento de 12 meses + holgura de binlog): …

**Colas de brandeo y de solicitudes de mascotas al final de la corrida .** Siempre las revisan los mismos 5 diseñadores.

| Cola | Estado | Inicio de A | Fin de A | Inicio de B | Fin de B |
|------|--------|-------------|----------|-------------|----------|
| `branding_requests` | `PENDING_REVIEW` | … | … | … | … |
| `branding_requests` | `DESIGN_IN_PROGRESS` | … | … | … | … |
| `catalog_integration_requests` | `PENDING` / `IN_REVIEW` | … | … | … | … |
| `catalog_integration_requests` | `APPROVED` / `ITEM_IN_PROGRESS` | … | … | … | … |

### 2.13 Sesgos declarados de la fase local

Siempre, en todas las corridas:

1. **CPU:** los núcleos M4 son más rápidos que una vCPU compartida de DO → resultados de CPU optimistas.
2. **Disco:** NVMe local frente a disco de red de DO → IOPS y latencia de escritura optimistas.
3. **Red:** RTT casi nulo (línea base de `db-latency.sh`: … ms) frente a AWS ↔ DO (~5-10 ms) → los p95 de los endpoints con muchas consultas por petición salen optimistas (N+1).
4. **k6 comparte máquina** con la API y la base → CPU de k6 medida (2.7) y corridas marcadas.

Otros que aplicaron: parámetros "como DO" de MySQL (buffer pool, `max_connections`, GTID, versión) son supuestos de documentación (se confirman en el cluster real); la API local no es una t4g.medium; … (completar).

### 2.14 PII en las salidas

`check-pii.sh` sobre: logs de la API de cada corrida, salida de k6, `results/<corrida>/`. Resultado: … Excepción nominal `F-GAMES-METRICS-STDOUT` (el `System.out` de `GameController` en `/games/metrics`, hallazgo conocido): líneas excluidas = …; se lista como hallazgo, no como limpio.
Archivo de prueba positivo (`testdata/pii-positive.txt`) sigue fallando: sí / no.

---

## 3. Fase nube (FINAL)

> Completar solo cuando la fase local esté completa (migraciones verificadas, decisión sobre los
> triggers de V9 tomada si fallaron, y corridas locales y smoke en verde). Estos son los números **finales**.

### 3.1 Ambiente

| Pieza | Valor |
|-------|-------|
| Región | AWS us-east-1 → DO NYC (`nyc1` / `nyc3`: …) |
| API en A y B-techo | EC2 t4g.medium, Tomcat 200, pool … |
| API en B | EC2 c7g.2xlarge, Tomcat 400, `-Xmx` al 70 %, pool … |
| Generador | EC2 m7g.xlarge con k6, WireMock y MinIO; CPU de k6 < 70 %: … |
| DO Managed MySQL | versión …, plan por corrida (3.5), nodos … |
| Variante de llaves primarias | `V202610041435` presente: sí / no → `sql_require_primary_key` tras migrar = … (¿coincide con la fase local?) |
| Duración de las corridas y fecha | … |

### 3.2 Cluster real y diferencias con los supuestos locales

| Variable | Valor local (`do-like.cnf` y plan 1 vCPU / 1 GB) | Valor en el cluster real de DO | ¿Difiere? | Se corrigió el archivo local |
|----------|---------------------------------------------------|---------------------------------|-----------|-------------------------------|
| `sql_require_primary_key` | … | … | … | … |
| `log_bin_trust_function_creators` | … | … | … | … |
| `gtid_mode` | … | … | … | … |
| `max_connections` (por plan) | … | … | … | … |
| `innodb_buffer_pool_size` (por plan) | … | … | … | … |
| `version` | … | … | … | … |
| `performance_schema` | … | … | … | … |
| `character_set_server` / `collation_server` / `time_zone` | … | … | … | … |

Flyway contra el cluster real (usuario sin `SUPER`): V1 a V… en `Success`: sí / no. Triggers = 4: sí / no.
**¿Pasó V9 en DO?** … → decisión sobre los triggers: …

### 3.3 Red y seguridad

| Comprobación | Resultado |
|--------------|-----------|
| RTT AWS ↔ DO (`db-latency.sh`, 500 × `SELECT 1`, antes de cada corrida) | … ms (min / prom / máx: …) |
| Línea base local para comparar | … ms |
| TLS: `tls-check.sql` → `sin_tls` | … (esperado 0), cifrado: … |
| `sslMode` de la API | `VERIFY_CA` con el CA de DO |
| Restricción por IP (Trusted sources = solo la Elastic IP de la API) | Elastic IP: … (enmascarada) |
| Prueba de restricción: conexión desde el generador | rechazada: sí / no |
| Security group de la API: salida solo a DO:25060 y al generador:8089/9000 | sí / no |
| Impacto del RTT en el p95 (comparación con local, endpoints con más consultas) | … |

### 3.4 Techo de VUs de la API de beta (B-techo)

Escalones de B con la API en t4g.medium, cortando cuando la API se satura (paso 1 del árbol).

| Escalón (VUs) | p95 | Error | CPU API | Tomcat ocupados / máx | Hikari pendientes | ¿Cumple los umbrales? |
|---------------|-----|-------|---------|-----------------------|-------------------|-----------------|
| 500 | … | … | … | … | … | … |
| 1.000 | … | … | … | … | … | … |
| 1.500 | … | … | … | … | … | … |
| 2.000 | … | … | … | … | … | … |

**Techo de la API de beta:** último escalón que cumple los umbrales = … VUs (… usuarios registrados al 20 %). Cuello: …

### 3.5 Corridas

| Id | Escenario | Plan DO | API | Fecha y hora | Duración | k6 salió con código | ¿Aguanta los umbrales? | CPU de k6 (< 70 %) |
|----|-----------|---------|-----|--------------|----------|---------------------|------------------|---------------------|
| N-A1 | A | … | t4g.medium | … | … | … | … | … |
| N-B1 | B | … | c7g.2xlarge | … | … | … | … | … |
| … | | | | | | | | |

### 3.6 Métricas por corrida y por plan

Fuentes en la nube: panel Insights de DO (capturas en `results/`), `snapshot.sql` antes y después y su delta,
`collect-api-metrics.sh`, `summary.js`. Si Insights no exporta series, anotar las lecturas manuales.

**Base de datos**

| Métrica | Fuente en la nube | N-A1 | N-B1 | … |
|-----------------|-------------------|------|------|---|
| CPU de la BD, promedio / pico | Insights | … | … | … |
| Memoria de la BD | Insights | … | … | … |
| Conexiones activas | `snapshot.sql` / Insights | … | … | … |
| Conexiones máximas usadas / `max_connections` | `snapshot.sql` | … | … | … |
| IOPS de lectura | Insights / `snapshot-delta.sh` | … | … | … |
| IOPS de escritura | Insights / `snapshot-delta.sh` | … | … | … |
| Consultas por segundo | `snapshot-delta.sh` | … | … | … |
| Consultas lentas | `top-digests.sql` | … | … | … |
| Tamaño en disco (datos + índices) | `table-sizes.sql` | … | … | … |

**API**

| Métrica | N-A1 | N-B1 | … |
|---------|------|------|---|
| Peticiones por segundo totales | … | … | … |
| Latencia p50 / p95 / p99 globales (ms) | … | … | … |
| Tasa de error global / rechazos esperados | … | … | … |
| CPU de la API, Tomcat, Hikari, heap | … | … | … |

### 3.7 Umbrales de aceptación por corrida

| Id | p95 global < 1 s | Error < 1 % | CPU BD < 75 % (promedio) | Sin agotar conexiones | **¿Aguanta?** |
|----|------------------|-------------|---------------------------|------------------------|----------------|
| N-A1 | … | … | … | … | … |
| N-B1 | … | … | … | … | … |

### 3.8 Tabla por endpoint y exclusiones

| Recorrido | Endpoint | Llamadas | p50 (ms) | p95 (ms) | p99 (ms) | Error | Rechazos esperados |
|-----------|----------|----------|----------|----------|----------|-------|--------------------|
| … | … | … | … | … | … | … | … |

Endpoints sin ejercitar y exclusiones: …

### 3.9 Árbol de diagnóstico y cuello de botella

| Id | 1. API saturada | 2. Pool chico | 3. Plan de BD saturado | 4. Cumple los umbrales | **Cuello** y acción tomada |
|----|-----------------|---------------|-------------------------|-------------------|-----------------------------|
| N-A1 | … | … | … | … | … |
| N-B1 | … | … | … | … | … |

### 3.10 Instancia mínima **final** y costo

Se empieza por el plan que pasó en local y se baja uno si sobra holgura (para no quedarse con un plan
sobredimensionado por el sesgo optimista).

| Escenario | Instancia mínima que cumple los umbrales | Plan DO | RDS equivalente | Plan + almacenamiento + standby (USD/mes) | RDS (USD/mes) |
|-----------|------------------------------------|---------|-----------------|---------------------------------------------|----------------|
| A | … | … | … | … | … |
| B | … | … | … | … | … |

### 3.11 Consultas lentas y tamaño por tabla

(mismas tablas que 2.11 y 2.12, con los datos de la nube) …

### 3.12 Comparación local vs nube

| Escenario | Plan que pasó en local (preliminar) | Plan que pasó en la nube (final) | Diferencia | Explicación (RTT, CPU, disco, k6 compartido, …) |
|-----------|--------------------------------------|----------------------------------|------------|--------------------------------------------------|
| A | … | … | … | … |
| B | … | … | … | … |

Sesgos locales que se confirmaron o no: … (¿el RTT subió el p95 de los endpoints con más consultas? ¿cuánto?).

### 3.13 Costo real de la prueba y "nada cobrando"

| Recurso | Horas | USD |
|---------|-------|-----|
| … | … | … |
| **Total** | | … |

Checklist del paso 12 de `GUIDE-cloud.md`, con fecha: EC2 Global View sin instancias …; volúmenes EBS …; IPs elásticas …; snapshots …;
AWS Billing del día …; DO: lista de Databases vacía …; uso del mes …. Cerrado el: …

### 3.14 PII en las salidas

`check-pii.sh` sobre los logs de la API (archivo del EC2), la salida de k6 y `results/`: … Excepción `F-GAMES-METRICS-STDOUT` (hallazgo conocido): líneas excluidas = …

---

## 4. Anexos

| Anexo | Ruta |
|-------|------|
| Resultados de cada corrida | `stress-tests/results/<fecha>-<escenario>-<plan>/` |
| Resumen de k6 (JSON y texto) | `…/summary-*.json` |
| CSV de `docker stats` (con el contenedor `k6`) | `…/docker-stats.csv` |
| Métricas de la API | `…/api-metrics.csv` |
| `snapshot` antes / después y delta | `…/snapshot-before.tsv`, `…/snapshot-after.tsv`, `…/snapshot-delta.tsv` |
| Top de consultas y tamaños | `…/top-digests.txt`, `…/table-sizes.txt` |
| Latencia y TLS | `…/db-latency.txt`, `…/tls-check.txt` |
| Verificación de PII | `…/check-pii.txt` |
| Capturas de Insights (nube) | `…` |

---

