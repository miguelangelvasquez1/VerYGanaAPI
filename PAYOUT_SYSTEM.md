# Sistema de Payouts — Documentación Técnica

## Índice

1. [Visión general](#1-visión-general)
2. [Arquitectura del sistema](#2-arquitectura-del-sistema)
3. [Modelos de datos](#3-modelos-de-datos)
4. [Flujo completo de un payout](#4-flujo-completo-de-un-payout)
5. [Métodos de pago de los comercials](#5-métodos-de-pago-de-los-comercials)
6. [Integración con Wompi Pagos a Terceros](#6-integración-con-wompi-pagos-a-terceros)
7. [Job scheduler](#7-job-scheduler)
8. [Webhook de confirmación](#8-webhook-de-confirmación)
9. [Tesorería y movimientos contables](#9-tesorería-y-movimientos-contables)
10. [Configuración](#10-configuración)
11. [Endpoints de la API](#11-endpoints-de-la-api)
12. [Operación y monitoreo](#12-operación-y-monitoreo)
13. [Manejo de errores y reintentos](#13-manejo-de-errores-y-reintentos)

---

## 1. Visión general

El sistema de payouts transfiere diariamente a cada comercial los ingresos netos generados por las ventas de sus productos durante el día.

**Un solo proveedor, dos productos:**

| Operación | Pasarela |
|---|---|
| Cobros a consumidores (Copayments, Subscriptions, Investments) | **Wompi — Checkout/Transacciones** |
| Desembolsos a comercials | **Wompi — Pagos a Terceros** |

> El proveedor de payouts fue originalmente Kushki. Kushki rechazó la alianza para el producto de payouts, así que el sistema migró a Wompi Pagos a Terceros — el mismo proveedor que ya se usaba para cobros. Esto elimina una integración externa completa y simplifica la conciliación (un solo dashboard, una sola relación comercial).

**Ciclo de vida de un payout:**

```
Ventas del día (Copayments COMPLETED)
        ↓
  scheduleDailyPayouts()          [11:00 PM Colombia]
  → agrupa ventas por commercial
  → calcula gross / commission / net
  → crea Payout(SCHEDULED)
        ↓
  processScheduledPayouts()
  → POST /payouts en Wompi (sin tokenización previa)
  → Payout → PROCESSING
        ↓
  Wompi webhook /wompi/payouts/events
  → APPROVED → Payout → PAID
  → DECLINED/FAILED → Payout → FAILED
        ↓
  retryFailedPayouts()            [11:30 PM Colombia]
  → reintenta payouts FAILED del ciclo anterior
```

**Frecuencia:** una vez al día. El comercial espera máximo 24 horas para recibir su pago, estándar en plataformas colombianas.

---

## 2. Arquitectura del sistema

```
┌─────────────────────────────────────────────────────────────────┐
│                        PayoutScheduler                          │
│   @Scheduled cron = "0 0 4 * * *"  (11 PM Colombia = 04 UTC)  │
└────────────┬────────────────────────────────────────────────────┘
             │ llama
             ▼
┌─────────────────────────┐      ┌──────────────────────────────┐
│    PayoutServiceImpl    │─────▶│     WompiPayoutClient        │
│                         │      │  POST /payouts (1 sola       │
│  scheduleDailyPayouts() │      │  llamada, sin tokenización)  │
│  processScheduledPayouts│      └──────────────┬───────────────┘
│  retryFailedPayouts()   │                     │ HTTP
│  handleWompiResult()    │                     ▼
└────────────┬────────────┘      ┌──────────────────────────────┐
             │                   │  Wompi Pagos a Terceros API   │
             │ persiste          └──────────────┬───────────────┘
             ▼                                  │ webhook
┌─────────────────────────┐                     ▼
│  PayoutRepository       │      ┌──────────────────────────────┐
│  WompiTransactionRepo   │◀─────│ WompiPayoutWebhookController │
│  PayoutMethodRepository │      │  POST /wompi/payouts/events  │
└─────────────────────────┘      └──────────────────────────────┘
```

**Archivos principales:**

```
config/wompi/
  WompiConfig.java                 → @ConfigurationProperties prefix="wompi" (cobros)
  WompiPayoutConfig.java           → @ConfigurationProperties prefix="wompi.payout"
  WompiWebClientConfig.java        → WebClient de cobros (Bearer private key)
  WompiPayoutWebClientConfig.java  → WebClient de payouts (API Key + Principal-User-Id)

models/finance/
  Payout.java                      → entidad principal de un pago diario
  WompiTransaction.java            → registro genérico de toda operación Wompi
                                      (cobros y payouts, distinguidos por `type`)
  PayoutMethod.java                → cuenta bancaria/Nequi/Daviplata del commercial
  PayoutItem.java                  → línea individual (copayment dentro de un payout)

models/enums/finance/
  PayoutStatus.java                → SCHEDULED | PROCESSING | PAID | FAILED
  WompiTransactionType.java        → CHARGE_* | TRANSFER_PAYOUT
  WompiTransactionStatus.java      → PENDING | APPROVED | DECLINED | ERROR | VOIDED

services/finance/
  PayoutServiceImpl.java           → lógica de negocio

schedulers/
  PayoutScheduler.java             → @Scheduled cron

services/wompi/
  WompiClient.java                 → HTTP client de cobros
  WompiPayoutClient.java           → HTTP client de Pagos a Terceros

controllers/wompi/
  WompiWebhookController.java        → endpoint POST /wompi/events (cobros)
  WompiWebhookDispatcher.java        → enruta eventos de cobros por tipo
  WompiPayoutWebhookController.java  → endpoint POST /wompi/payouts/events

repositories/finance/
  PayoutRepository.java
  WompiTransactionRepository.java
  PayoutMethodRepository.java

dtos/wompi/
  WompiPayoutRequestDTO.java        → body de POST /payouts
  WompiPayoutResponseDTO.java
  WompiPayoutBalanceResponseDTO.java → respuesta de GET /accounts
  WompiPayoutWebhookEvent.java       → payload del webhook de Pagos a Terceros
```

---

## 3. Modelos de datos

### 3.1 Payout

Representa el pago diario batch a un comercial. Uno por comercial por día.

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | UUID | PK inmutable |
| `commercial` | CommercialDetails | empresario receptor |
| `grossAmountCents` | Long | suma de `total_amount_cents` de todos los copayments del período |
| `commissionCents` | Long | parte que retiene VeryGana |
| `netAmountCents` | Long | `gross - commission` — lo que recibe el commercial |
| `commissionPctApplied` | Integer | snapshot del % aplicado en este payout (auditoría) |
| `status` | PayoutStatus | estado actual del pago |
| `wompiTransaction` | WompiTransaction | FK — se vincula cuando el job pasa a PROCESSING |
| `scheduledAt` | ZonedDateTime | cuándo creó el job este payout |
| `paidAt` | ZonedDateTime | cuándo Wompi confirmó el pago |
| `periodStart` / `periodEnd` | ZonedDateTime | rango de ventas que cubre |
| `failureReason` | String | razón del rechazo (si status=FAILED) |
| `retryCount` | Integer | cuántas veces se reintentó |

**Diagrama de estados:**

```
         scheduleDailyPayouts()
SCHEDULED ──────────────────────────────▶ PROCESSING
    │         processScheduledPayouts()        │
    │         (Wompi /payouts exitoso)         │
    │                                     webhook APPROVED
    │                                          │
    └──────────────────────────────────────▶ PAID
    │
    │         Wompi /payouts falla o
    └──────── webhook DECLINED/FAILED ──────▶ FAILED
                                                │
                              retryFailedPayouts() → SCHEDULED (de nuevo)
```

### 3.2 WompiTransaction

Registro genérico e inmutable de cualquier operación con Wompi (cobros y payouts), distinguido por el campo `type`. Sirve para auditoría, reconciliación y soporte. Es el mismo modelo que ya se usaba para cobros (`CHARGE_COPAYMENT`, `CHARGE_PLAN_SUBSCRIPTION`, `CHARGE_BUSINESS_DEPOSIT`) — los payouts reutilizan el tipo `TRANSFER_PAYOUT`.

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | UUID | PK |
| `wompiId` | String | ID que asigna Wompi a la operación |
| `type` | WompiTransactionType | `TRANSFER_PAYOUT` para payouts |
| `amountInCents` | Long | monto transferido en centavos |
| `status` | WompiTransactionStatus | estado según Wompi |
| `reference` | String | `"VG-PAYOUT-{payoutId}"` — nuestra referencia, clave de reconciliación del webhook |
| `metadata` | JSON | payload completo del webhook para auditoría |

### 3.3 PayoutMethod

Cuenta destino verificada de un comercial. Un comercial puede tener varios pero el job usa el primero VERIFIED y activo.

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | PayoutMethodType | `BANK_TRANSFER` \| `NEQUI` \| `DAVIPLATA` |
| `bankCode` | String | `bankId` (UUID) del catálogo `GET /banks` de Wompi |
| `accountNumber` | String | número de cuenta |
| `bankAccountType` | BankAccountType | `SAVINGS` \| `CHECKING` (se traduce a `AHORROS`/`CORRIENTE` al llamar a Wompi) |
| `phoneNumber` | String | para Nequi/Daviplata |
| `accountHolderName` | String | nombre del titular |
| `accountHolderDoc` | String | cédula o NIT |
| `verificationStatus` | VerificationStatus | ver flujo abajo |

**Flujo de verificación:**

```
BANK_TRANSFER:
  PENDING_VERIFICATION → UNDER_REVIEW → VERIFIED
                                     → REJECTED

NEQUI / DAVIPLATA (OTP vía Twilio):
  PENDING_VERIFICATION → AWAITING_OTP → VERIFIED
                                      → REJECTED (max intentos)
```

**Regla antifraude:** el primer payout a un método verificado queda retenido 24h (`firstPayoutCompleted = false`). A partir del segundo pago, el ciclo es normal.

### 3.4 PayoutItem

Línea de detalle dentro de un Payout. Registra qué copayment (y a qué commercial) corresponde cada monto.

| Campo | Tipo | Descripción |
|---|---|---|
| `payout` | Payout | FK al payout del día |
| `copayment` | Copayment | FK al copago origen |
| `amountCents` | Long | parte de este copayment para este commercial |

---

## 4. Flujo completo de un payout

### Fase 1 — scheduleDailyPayouts()

Se ejecuta a las 11 PM Colombia (04:00 UTC).

```
1. Buscar Copayment(status=COMPLETED) del período sin PayoutItem asociado
2. Para cada copayment → recorrer sus PurchaseItems
3. Agrupar por commercial_id
4. Por cada grupo:
   a. grossAmountCents  = Σ item.subtotalCents
   b. commissionCents   = Σ item.commissionCents
   c. netAmountCents    = Σ item.netToCommercialCents
   d. commissionPctApplied = snapshot del % del plan actual
   e. Crear Payout(status=SCHEDULED)
   f. Crear PayoutItem por cada copayment del grupo
5. Idempotencia: si ya existe PayoutItem(copayment, commercial) → saltar
```

**Por qué batch y no en tiempo real:**
- El batch agrupa todas las ventas del día en 1 transferencia por comercial.
- Wompi cobra por transacción — el batch reduce costos operativos significativamente.

### Fase 2 — processScheduledPayouts()

Se ejecuta inmediatamente después de la Fase 1.

```
Para cada Payout(status=SCHEDULED):
  1. Obtener PayoutMethod VERIFIED y activo del commercial
  2. Armar el request según el canal (BANK_TRANSFER / NEQUI / DAVIPLATA)
  3. POST /payouts  → una sola llamada, sin tokenización previa
  4. Crear WompiTransaction(type=TRANSFER_PAYOUT, status=PENDING)
  5. Vincular al Payout → Payout(status=PROCESSING)
  6. Si Wompi rechaza el request → Payout(status=FAILED, failureReason)
```

### Fase 3 — Webhook de Wompi

Wompi llama a `POST /wompi/payouts/events` cuando la transferencia se resuelve.

```
Si status=APPROVED:
  → WompiTransaction(status=APPROVED)
  → TreasuryService.registerPayoutSent(netAmountCents, payoutId)
  → Payout(status=PAID, paidAt=NOW())

Si status=DECLINED o FAILED:
  → WompiTransaction(status=DECLINED/ERROR)
  → Payout(status=FAILED, failureReason=status)
  → Se reintentará en el ciclo de la noche siguiente
```

### Fase 4 — retryFailedPayouts()

Se ejecuta a las 11:30 PM Colombia (04:30 UTC).

```
1. Buscar Payout(status=FAILED) del período anterior
2. Incrementar retryCount
3. Cambiar a SCHEDULED
4. Volver a ejecutar processScheduledPayouts() para ese payout
```

---

## 5. Métodos de pago de los comercials

### Registro de un método

`POST /commercial/payout-methods` — el commercial registra su cuenta.

Dependiendo del tipo:

**BANK_TRANSFER** — requiere: `bankCode` (bankId de Wompi), `accountNumber`, `bankAccountType`, `accountHolderName`, `accountHolderDoc`, `accountHolderDocType`, `alias`.

**NEQUI / DAVIPLATA** — requiere: `phoneNumber`, `accountHolderName`, `accountHolderDoc`, `alias`. El `bankId` de Wompi para estos dos canales no lo elige el commercial — es una constante configurada (`wompi.payout.nequi-bank-id` / `wompi.payout.daviplata-bank-id`).

### Verificación BANK_TRANSFER

1. El commercial registra la cuenta → `PENDING_VERIFICATION`
2. Un admin revisa desde `GET /api/admin/payout-methods?status=UNDER_REVIEW`
3. Admin aprueba → `VERIFIED` | Admin rechaza → `REJECTED` con razón

### Verificación NEQUI / DAVIPLATA (automática)

1. El commercial registra el número → `PENDING_VERIFICATION`
2. Sistema envía OTP vía Twilio Verify al número registrado → `AWAITING_OTP`
3. Commercial confirma el OTP en la app → `VERIFIED`
4. Si agota los intentos → `REJECTED`

### Regla de primer pago (antifraude)

El flag `firstPayoutCompleted` empieza en `false`. Cuando el job ejecuta el primer payout exitoso al método, lo marca `true`. Hasta que esto ocurra, el job retiene el pago 24h adicionales para revisión antifraude (comportamiento estilo Airbnb).

---

## 6. Integración con Wompi Pagos a Terceros

### Credenciales

| Variable | Descripción |
|---|---|
| `WOMPI_PAYOUT_API_KEY` | Autenticación en el header `Authorization: Bearer` del WebClient de payouts |
| `WOMPI_PAYOUT_PRINCIPAL_USER_ID` | ID Usuario Principal, header `Principal-User-Id` |
| `WOMPI_PAYOUT_EVENTS_KEY` | Secreto para validar la firma del webhook de payouts |
| `WOMPI_PAYOUT_ACCOUNT_ID` | Cuenta de origen de las dispersiones (`GET /accounts`) |
| `WOMPI_PAYOUT_NEQUI_BANK_ID` | `bankId` de Wompi que representa a Nequi en el catálogo `/banks` |
| `WOMPI_PAYOUT_DAVIPLATA_BANK_ID` | `bankId` de Wompi que representa a Daviplata |

> **Pendiente de verificar en sandbox:** que `GET /banks` efectivamente incluya entradas para Nequi y Daviplata, y el nombre exacto de los headers de autenticación (la documentación pública de Wompi no los detalla). Ver `WompiPayoutWebClientConfig` y `WompiPayoutConfig`.

### Ambientes

| Ambiente | Base URL |
|---|---|
| Sandbox | `https://api.sandbox.payouts.wompi.co/v1` |
| Producción | `https://api.payouts.wompi.co/v1` |

Cambiar en `application-dev.yml` / `application-prod.yml` bajo la clave `wompi.payout.api-base-url`.

### Endpoints usados

| Método | Path | Propósito |
|---|---|---|
| `GET` | `/accounts` | Consultar balance disponible (`balanceInCents`) |
| `POST` | `/payouts` | Crear el payout — sin tokenización previa, todo en una llamada |
| `GET` | `/payouts/{id}` | Consultar estado de un payout (uso manual/soporte) |
| `GET` | `/banks` | Catálogo de bancos/canales disponibles (incluye, a confirmar, Nequi/Daviplata) |

### Fondeo del balance de la cuenta de dispersión

Igual que con el proveedor anterior, el fondeo se hace por transferencia bancaria manual a la cuenta que Wompi indique para la cuenta de dispersión configurada. El `PayoutScheduler` consulta el balance antes de cada ciclo y lanza una advertencia en los logs si está por debajo del umbral configurado (`wompi.payout.min-balance-alert-cents`).

### Ciclos ACH Colombia

Las transferencias a bancos distintos de Bancolombia/Nequi/Bre-B siguen los ciclos ACH del sistema financiero colombiano: solo se ejecutan en días hábiles en horario bancario. Bancolombia, Nequi y Bre-B sí tienen liquidación inmediata según la documentación de Wompi.

---

## 7. Job Scheduler

`PayoutScheduler` — `@Scheduled` con cron configurable.

### Configuración de tiempos

```yaml
wompi:
  payout:
    cron: "0 0 4 * * *"       # 11:00 PM Colombia = 04:00 UTC
    retry-cron: "0 30 4 * * *" # 11:30 PM Colombia = 04:30 UTC
    min-balance-alert-cents: 5000000  # alerta si balance < $50.000 COP
```

### Por qué 11 PM y no medianoche

- Las 11 PM da tiempo a que todos los Copayments del día estén `COMPLETED` y los webhooks de Wompi (cobros) hayan llegado.
- No usar medianoche: esos webhooks pueden tardar varios minutos y podrían quedar fuera del período.

### Log de ejecución

Cada ciclo produce entradas como:

```
[PAYOUT-SCHEDULER] Balance Wompi Payouts OK: 2500000 COP
[PAYOUT-SCHEDULER] Encontrados 47 copayments COMPLETED.
[PAYOUT-SCHEDULER] Payout SCHEDULED: id=..., commercial=Tienda XYZ, net=185000
[PAYOUT-SCHEDULER] Payout → PROCESSING: id=..., wompiId=wp_123...
[PAYOUT-SCHEDULER] Ciclo diario completado.
[PAYOUT-RETRY] Sin payouts FAILED para reintentar.
```

---

## 8. Webhook de confirmación

### Endpoint

```
POST /wompi/payouts/events
```

Distinto del webhook de cobros (`POST /wompi/events`): el payload de Pagos a Terceros usa una estructura distinta (camelCase, con `failureReason`) y Wompi lo registra por separado en su dashboard (sección "Pagos a Terceros" → "Programadores"). No requiere autenticación JWT (está en `PublicPaths`).

**Importante:** el endpoint siempre responde `200 OK`, incluso ante errores internos. Si responde `4xx` o `5xx`, Wompi reintenta el webhook hasta 3 veces.

### Configuración del webhook en Wompi

Desde el dashboard de Wompi: **Desarrollo → Programadores → Pagos a Terceros → registrar URL**.

Para desarrollo local usar **ngrok**:

```bash
ngrok http 8080
# Registrar en el dashboard: https://abc123.ngrok.io/wompi/payouts/events
```

### Payload del webhook

```json
{
  "event": "transaction.updated",
  "data": {
    "transaction": {
      "id": "04a6e53d-a244-4140-ab9e-48fa541f9fe5",
      "reference": "VG-PAYOUT-{payoutId}",
      "status": "APPROVED",
      "amountInCents": 185000,
      "failureReason": { "code": "C01", "message": "..." }
    }
  },
  "signature": { "checksum": "...", "properties": ["transaction.id", "transaction.status"] },
  "timestamp": 1747673128600
}
```

Wompi también envía `"event": "payout.updated"` a nivel de lote (`data.payout`) — se ignora deliberadamente, porque cada `Payout` se procesa individualmente, no por lote.

### Validación de firma

`SHA256(prop1Value + prop2Value + ... + timestamp + eventsKey)`, con las propiedades listadas en `signature.properties`. Mismo algoritmo que el webhook de cobros, pero con la `eventsKey` propia de Pagos a Terceros (`WOMPI_PAYOUT_EVENTS_KEY`). Ver `WompiPayoutClient.isValidWebhookSignature`.

### Idempotencia

El handler verifica si el `Payout` ya dejó de estar en `PROCESSING` antes de aplicar el resultado. Si Wompi envía el mismo webhook más de una vez, el segundo se ignora silenciosamente.

---

## 9. Tesorería y movimientos contables

Cuando un payout es `PAID`, el sistema llama a `TreasuryService.registerPayoutSent(netAmountCents, payoutId)`. Esto genera un `TreasuryMovement` del tipo:

```
fromAccount: PAYOUTS_PENDING
toAccount: [cuenta externa del commercial]
concept: PAYOUT_SENT
referenceId: payoutId
```

La comisión de VeryGana fue retenida al momento de cada venta (en `handleApproved()` del servicio de copagos), por lo que `scheduleDailyPayouts()` **no** llama a `retainCommission()` — solo registra el snapshot de `commissionCents` para auditoría del payout.

---

## 10. Configuración

### application-dev.yml / application-prod.yml

```yaml
wompi:
  public-key: ${WOMPI_PUBLIC_KEY}
  private-key: ${WOMPI_PRIVATE_KEY}
  integrity-secret: ${WOMPI_INTEGRITY_SECRET}
  events-key: ${WOMPI_EVENTS_KEY}
  checkout-base-url: https://checkout.wompi.co/p/
  api-base-url: https://production.wompi.co/v1
  payout:
    api-key: ${WOMPI_PAYOUT_API_KEY}
    principal-user-id: ${WOMPI_PAYOUT_PRINCIPAL_USER_ID}
    events-key: ${WOMPI_PAYOUT_EVENTS_KEY}
    account-id: ${WOMPI_PAYOUT_ACCOUNT_ID}
    nequi-bank-id: ${WOMPI_PAYOUT_NEQUI_BANK_ID}
    daviplata-bank-id: ${WOMPI_PAYOUT_DAVIPLATA_BANK_ID}
    api-base-url: https://api.payouts.wompi.co/v1
    cron: "0 0 4 * * *"
    retry-cron: "0 30 4 * * *"
    min-balance-alert-cents: 5000000
```

### Variables de entorno requeridas (payouts)

| Variable | Descripción |
|---|---|
| `WOMPI_PAYOUT_API_KEY` | Clave de autenticación de Pagos a Terceros |
| `WOMPI_PAYOUT_PRINCIPAL_USER_ID` | ID Usuario Principal |
| `WOMPI_PAYOUT_EVENTS_KEY` | Secreto de firma del webhook de payouts |
| `WOMPI_PAYOUT_ACCOUNT_ID` | Cuenta de origen de las dispersiones |
| `WOMPI_PAYOUT_NEQUI_BANK_ID` | bankId de Nequi en el catálogo de Wompi |
| `WOMPI_PAYOUT_DAVIPLATA_BANK_ID` | bankId de Daviplata en el catálogo de Wompi |

---

## 11. Endpoints de la API

### Commercial (rol: `ROLE_COMMERCIAL`)

| Método | Path | Descripción |
|---|---|---|
| `GET` | `/commercial/wallet/me` | Ver balance y estado del wallet |
| `POST` | `/commercial/wallet/withdraw` | Solicitar retiro manual |
| `GET` | `/commercial/wallet/me/transactions` | Historial de depósitos (paginado) |
| `GET` | `/commercial/wallet/me/payouts` | Historial de payouts recibidos (paginado) |

### Admin (rol: `ROLE_ADMIN`)

| Método | Path | Descripción |
|---|---|---|
| `GET` | `/api/admin/payouts?date=YYYY-MM-DD` | Listar payouts de una fecha (hoy si no se pasa) |

### Webhook (público)

| Método | Path | Descripción |
|---|---|---|
| `POST` | `/wompi/events` | Confirmaciones de cobros — no requiere JWT |
| `POST` | `/wompi/payouts/events` | Confirmaciones de payouts — no requiere JWT |

---

## 12. Operación y monitoreo

### Alerta de balance bajo

El scheduler loguea una advertencia `WARN` si el balance de la cuenta de dispersión de Wompi está por debajo del umbral. En producción se recomienda conectar este log a un sistema de alertas (PagerDuty, Slack, etc.) para que el equipo de operaciones recargue el balance a tiempo.

```
⚠ Balance Wompi Payouts bajo: 45000 COP (umbral: 50000 COP).
  Recargar la cuenta de dispersión de Wompi.
```

### Cuándo recargar el balance

Estimación de consumo diario con 30 comercials activos:

| Escenario | Ventas promedio/commercial/día | Payout neto total |
|---|---|---|
| Conservador | $30.000 COP | $900.000 COP |
| Moderado | $80.000 COP | $2.400.000 COP |
| Alto | $150.000 COP | $4.500.000 COP |

**Recomendación:** mantener al menos 7 días de consumo proyectado en el balance. Con el escenario moderado, eso implica un saldo mínimo de ~$17.000.000 COP. El tope diario confirmado con Wompi para la tarifa por transacción es de $1.500.000.000 COP.

### Consultar estado de un payout

```
GET /api/admin/payouts?date=2025-03-15
```

Respuesta incluye: `id`, `commercial`, `gross`, `commission`, `net`, `status`, `scheduledAt`, `paidAt`, `failureReason`, `retryCount`.

---

## 13. Manejo de errores y reintentos

### Matriz de fallos

| Escenario | Resultado inmediato | Acción automática |
|---|---|---|
| Commercial sin PayoutMethod verificado | `Payout(FAILED)` | Log WARN — no se reintenta hasta que el commercial verifique un método |
| Wompi `/payouts` falla (timeout, 5xx) | `WompiApiException` capturada → `Payout(FAILED)` | Se reintenta en el ciclo de reintentos (11:30 PM) |
| Wompi `/payouts` devuelve status de rechazo | `Payout(FAILED, failureReason)` | Se reintenta en el ciclo de reintentos |
| Webhook DECLINED/FAILED de Wompi | `Payout(FAILED)` | Se reintenta en el ciclo siguiente |
| Balance de la cuenta de dispersión insuficiente | Wompi rechaza `/payouts` | Log WARN antes del ciclo — equipo ops recarga el balance |
| Webhook duplicado | Ignorado (idempotencia) | — |

### Límite de reintentos

No existe un límite fijo de reintentos en la implementación actual. Cada noche que el payout esté en `FAILED` se reintentará. Se recomienda agregar un tope (ej: `retryCount >= 5`) y pasar el payout a un estado `EXHAUSTED` para revisión manual del admin.

### Trazabilidad

Cada payout tiene `id` (UUID) trazable en:
- `Payout.id` → tabla `payouts`
- `WompiTransaction.reference` = `"VG-PAYOUT-{payoutId}"`
- `WompiTransaction.wompiId` = ID en el dashboard de Wompi
- `TreasuryMovement.referenceId` = `payoutId`
- Logs con `[PAYOUT-SCHEDULER]`, `[WOMPI PAYOUT WEBHOOK]` como prefijos filtrables
