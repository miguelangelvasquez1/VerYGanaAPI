# Sistema de Payouts — Documentación Técnica

## Índice

1. [Visión general](#1-visión-general)
2. [Arquitectura del sistema](#2-arquitectura-del-sistema)
3. [Modelos de datos](#3-modelos-de-datos)
4. [Flujo completo de un payout](#4-flujo-completo-de-un-payout)
5. [Métodos de pago de los comercials](#5-métodos-de-pago-de-los-comercials)
6. [Integración con Kushki](#6-integración-con-kushki)
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

**Separación de pasarelas:**

| Operación | Pasarela |
|---|---|
| Cobros a consumidores (Copayments, Subscriptions, Investments) | **Wompi** |
| Desembolsos a comercials | **Kushki** |

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
  → tokeniza cuenta destino en Kushki
  → inicia transferencia ACH
  → Payout → PROCESSING
        ↓
  Kushki webhook /kushki/events
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
│    PayoutServiceImpl    │─────▶│     KushkiPayoutClient       │
│                         │      │  POST /payouts/transfer/v1/  │
│  scheduleDailyPayouts() │      │       tokens + init          │
│  processScheduledPayouts│      └──────────────┬───────────────┘
│  retryFailedPayouts()   │                     │ HTTP
│  handleKushkiWebhook()  │                     ▼
└────────────┬────────────┘      ┌──────────────────────────────┐
             │                   │   API Kushki (UAT/Producción) │
             │ persiste          └──────────────┬───────────────┘
             ▼                                  │ webhook
┌─────────────────────────┐                     ▼
│  PayoutRepository       │      ┌──────────────────────────────┐
│  KushkiTransactionRepo  │◀─────│  KushkiWebhookController     │
│  PayoutMethodRepository │      │  POST /kushki/events         │
└─────────────────────────┘      └──────────────────────────────┘
```

**Archivos principales:**

```
config/kushki/
  KushkiConfig.java                → @ConfigurationProperties prefix="kushki"
  KushkiWebClientConfig.java       → WebClient con header Private-Merchant-Id

models/finance/
  Payout.java                      → entidad principal de un pago diario
  KushkiTransaction.java           → registro de cada llamada a Kushki
  PayoutMethod.java                → cuenta bancaria/Nequi/Daviplata del commercial
  PayoutItem.java                  → línea individual (copayment dentro de un payout)

models/enums/finance/
  PayoutStatus.java                → SCHEDULED | PROCESSING | PAID | FAILED
  KushkiTransactionStatus.java     → INITIALIZED | PENDING | APPROVED | DECLINED | FAILED

services/finance/
  PayoutServiceImpl.java           → lógica de negocio
  PayoutScheduler.java             → @Scheduled cron

services/kushki/
  KushkiPayoutClient.java          → HTTP client para la API de Kushki

controllers/kushki/
  KushkiWebhookController.java     → endpoint POST /kushki/events

repositories/finance/
  PayoutRepository.java
  KushkiTransactionRepository.java
  PayoutMethodRepository.java

dtos/kushki/
  KushkiTokenRequestDTO.java       → body para tokenizar cuenta destino
  KushkiTokenResponseDTO.java
  KushkiTransferRequestDTO.java    → body para iniciar transferencia
  KushkiTransferResponseDTO.java
  KushkiBalanceResponseDTO.java    → respuesta de consulta de balance
  KushkiWebhookEvent.java          → payload del webhook entrante
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
| `kushkiTransaction` | KushkiTransaction | FK — se vincula cuando el job pasa a PROCESSING |
| `scheduledAt` | ZonedDateTime | cuándo creó el job este payout |
| `paidAt` | ZonedDateTime | cuándo Kushki confirmó el pago |
| `periodStart` / `periodEnd` | ZonedDateTime | rango de ventas que cubre |
| `failureReason` | String | razón del rechazo (si status=FAILED) |
| `retryCount` | Integer | cuántas veces se reintentó |

**Diagrama de estados:**

```
         scheduleDailyPayouts()
SCHEDULED ──────────────────────────────▶ PROCESSING
    │         processScheduledPayouts()        │
    │         (Kushki /init exitoso)           │
    │                                     webhook APPROVED
    │                                          │
    └──────────────────────────────────────▶ PAID
    │
    │         Kushki /init falla o
    └──────── webhook DECLINED/FAILED ──────▶ FAILED
                                                │
                              retryFailedPayouts() → SCHEDULED (de nuevo)
```

### 3.2 KushkiTransaction

Registro inmutable de cada llamada a la API de Kushki. Sirve para auditoría, reconciliación y soporte.

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | UUID | PK |
| `kushkiTransferId` | String | ID que asigna Kushki — null hasta que /init responde |
| `internalReference` | String | `"VG-PAYOUT-{payoutId}"` — nuestra referencia |
| `amountCents` | Long | monto transferido en centavos |
| `status` | KushkiTransactionStatus | estado según Kushki |
| `metadata` | JSON | payload completo del webhook para auditoría |
| `failureReason` | String | mensaje de error si DECLINED o FAILED |

### 3.3 PayoutMethod

Cuenta destino verificada de un comercial. Un comercial puede tener varios pero el job usa el primero VERIFIED y activo.

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | PayoutMethodType | `BANK_TRANSFER` \| `NEQUI` \| `DAVIPLATA` |
| `bankCode` | String | código ACH del banco (ej: "1007" = Bancolombia) |
| `accountNumber` | String | número de cuenta |
| `bankAccountType` | BankAccountType | `SAVINGS` \| `CHECKING` |
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
- Una venta individual requeriría 2 llamadas a Kushki (tokenizar + transferir).
- El batch agrupa todas las ventas del día en 1 transferencia por comercial.
- Kushki cobra por transacción — el batch reduce costos operativos significativamente.

### Fase 2 — processScheduledPayouts()

Se ejecuta inmediatamente después de la Fase 1.

```
Para cada Payout(status=SCHEDULED):
  1. Obtener PayoutMethod VERIFIED y activo del commercial
  2. POST /payouts/transfer/v1/tokens  → obtener token de cuenta destino
  3. POST /payouts/transfer/v1/init    → iniciar transferencia ACH
  4. Crear KushkiTransaction(status=PENDING)
  5. Vincular al Payout → Payout(status=PROCESSING)
  6. Si /init falla → Payout(status=FAILED, failureReason)
```

### Fase 3 — Webhook de Kushki

Kushki llama a `POST /kushki/events` cuando la transferencia se resuelve en el sistema ACH.

```
Si status=APPROVED:
  → KushkiTransaction(status=APPROVED)
  → TreasuryService.registerPayoutSent(netAmountCents, payoutId)
  → Payout(status=PAID, paidAt=NOW())

Si status=DECLINED o FAILED:
  → KushkiTransaction(status=DECLINED/FAILED)
  → Payout(status=FAILED, failureReason=message)
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

**BANK_TRANSFER** — requiere: `bankCode`, `accountNumber`, `bankAccountType`, `accountHolderName`, `accountHolderDoc`, `accountHolderDocType`, `alias`.

**NEQUI / DAVIPLATA** — requiere: `phoneNumber`, `accountHolderName`, `accountHolderDoc`, `alias`.

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

## 6. Integración con Kushki

### Credenciales

| Variable | Descripción |
|---|---|
| `KUSHKI_PRIVATE_KEY` | Autenticación en el header `Private-Merchant-Id` |
| `KUSHKI_PUBLIC_KEY` | Para operaciones del frontend (no usado en payouts) |

### Ambientes

| Ambiente | Base URL |
|---|---|
| Sandbox (UAT) | `https://api-uat.kushkipagos.com` |
| Producción | `https://api.kushkipagos.com` |

Cambiar en `application-dev.yml` / `application-prod.yml` bajo la clave `kushki.api-base-url`.

### Endpoints usados

| Método | Path | Propósito |
|---|---|---|
| `GET` | `/payouts/balance/v1` | Consultar balance disponible |
| `POST` | `/payouts/transfer/v1/tokens` | Tokenizar cuenta destino |
| `POST` | `/payouts/transfer/v1/init` | Iniciar transferencia ACH |

### Fondeo del balance Kushki

Kushki **no tiene API** para recargar el balance programáticamente. El fondeo se hace por transferencia bancaria manual:

> **Cuenta Davivienda Ahorros** `0570479470007937`
> **Titular:** Kushki Colombia SAS
> **NIT:** 9010003304
> **Máximo por día:** 100,000,000 COP

El `PayoutScheduler` consulta el balance antes de cada ciclo y lanza una advertencia en los logs si está por debajo del umbral configurado (`kushki.payout.min-balance-alert-cents`).

### Ciclos ACH Colombia

Las transferencias bancarias siguen los ciclos ACH del sistema financiero colombiano: solo se ejecutan en días hábiles en horario bancario. Una transferencia enviada a las 11 PM puede llegar al comercial el siguiente día hábil. Esto es comportamiento estándar y no depende de Kushki.

---

## 7. Job Scheduler

`PayoutScheduler` — `@Scheduled` con cron configurable.

### Configuración de tiempos

```yaml
kushki:
  payout:
    cron: "0 0 4 * * *"       # 11:00 PM Colombia = 04:00 UTC
    retry-cron: "0 30 4 * * *" # 11:30 PM Colombia = 04:30 UTC
    min-balance-alert-cents: 5000000  # alerta si balance < $50.000 COP
```

### Por qué 11 PM y no medianoche

- Las 11 PM da tiempo a que todos los Copayments del día estén `COMPLETED` y los webhooks de Wompi hayan llegado.
- No usar medianoche: los webhooks de Wompi pueden tardar varios minutos y podrían quedar fuera del período.

### Log de ejecución

Cada ciclo produce entradas como:

```
[PAYOUT-SCHEDULER] Balance Kushki OK: 2500000 COP
[PAYOUT-SCHEDULER] Encontrados 47 copayments COMPLETED.
[PAYOUT-SCHEDULER] Payout SCHEDULED: id=..., commercial=Tienda XYZ, net=185000
[PAYOUT-SCHEDULER] Payout → PROCESSING: id=..., transferId=TRF-123...
[PAYOUT-SCHEDULER] Ciclo diario completado.
[PAYOUT-RETRY] Sin payouts FAILED para reintentar.
```

---

## 8. Webhook de confirmación

### Endpoint

```
POST /kushki/events
```

Kushki llama a este endpoint cuando una transferencia cambia de estado. No requiere autenticación JWT (está en `PublicPaths`).

**Importante:** el endpoint siempre responde `200 OK`, incluso ante errores internos. Si responde `4xx` o `5xx`, Kushki reintenta el webhook indefinidamente.

### Configuración del webhook en Kushki

El webhook de Kushki **no es self-service en sandbox** — hay que notificar por email al equipo de Kushki:

- **Para sandbox:** enviar correo a soporte de Kushki con la URL `https://tu-ngrok-url/kushki/events`
- **Para producción:** configurar desde el dashboard de Kushki

Para desarrollo local usar **ngrok**:

```bash
ngrok http 8080
# Notificar a Kushki: https://abc123.ngrok.io/kushki/events
```

### Payload del webhook

```json
{
  "transferId": "TRF-abc123",
  "merchantTransferReference": "VG-PAYOUT-{uuid}",
  "status": "APPROVED",
  "amount": 185000.00,
  "currency": "COP",
  "code": "000",
  "message": "Transacción aprobada"
}
```

### Idempotencia

El handler verifica si el `KushkiTransaction` ya está en estado terminal antes de procesar. Si Kushki envía el mismo webhook más de una vez, el segundo se ignora silenciosamente.

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
kushki:
  private-key: ${KUSHKI_PRIVATE_KEY}
  public-key: ${KUSHKI_PUBLIC_KEY}
  api-base-url: https://api-uat.kushkipagos.com   # sandbox
  # api-base-url: https://api.kushkipagos.com     # producción
  payout:
    cron: "0 0 4 * * *"
    retry-cron: "0 30 4 * * *"
    min-balance-alert-cents: 5000000
```

### Variables de entorno requeridas

| Variable | Descripción |
|---|---|
| `KUSHKI_PRIVATE_KEY` | Clave privada del merchant en Kushki |
| `KUSHKI_PUBLIC_KEY` | Clave pública del merchant en Kushki |

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
| `POST` | `/kushki/events` | Confirmaciones de Kushki — no requiere JWT |

---

## 12. Operación y monitoreo

### Alerta de balance bajo

El scheduler loguea una advertencia `WARN` si el balance de Kushki está por debajo del umbral. En producción se recomienda conectar este log a un sistema de alertas (PagerDuty, Slack, etc.) para que el equipo de operaciones recargue el balance a tiempo.

```
⚠ Balance Kushki bajo: 45000 COP (umbral: 50000 COP). 
  Recargar cuenta Davivienda de Kushki Colombia.
```

### Cuándo recargar el balance de Kushki

Estimación de consumo diario con 30 comercials activos:

| Escenario | Ventas promedio/commercial/día | Payout neto total |
|---|---|---|
| Conservador | $30.000 COP | $900.000 COP |
| Moderado | $80.000 COP | $2.400.000 COP |
| Alto | $150.000 COP | $4.500.000 COP |

**Recomendación:** mantener al menos 7 días de consumo proyectado en el balance. Con el escenario moderado, eso implica un saldo mínimo de ~$17.000.000 COP.

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
| Kushki `/tokens` falla (timeout, 5xx) | `KushkiApiException` capturada → `Payout(FAILED)` | Se reintenta en el ciclo de reintentos (11:30 PM) |
| Kushki `/init` devuelve código de error | `Payout(FAILED, failureReason)` | Se reintenta en el ciclo de reintentos |
| Webhook DECLINED de Kushki | `Payout(FAILED)` | Se reintenta en el ciclo siguiente |
| Balance Kushki insuficiente | Kushki rechaza `/init` | Log WARN antes del ciclo — equipo ops recarga el balance |
| Webhook duplicado | Ignorado (idempotencia) | — |

### Límite de reintentos

No existe un límite fijo de reintentos en la implementación actual. Cada noche que el payout esté en `FAILED` se reintentará. Se recomienda agregar un tope (ej: `retryCount >= 5`) y pasar el payout a un estado `EXHAUSTED` para revisión manual del admin.

### Trazabilidad

Cada payout tiene `id` (UUID) trazable en:
- `Payout.id` → tabla `payouts`
- `KushkiTransaction.internalReference` = `"VG-PAYOUT-{payoutId}"`
- `KushkiTransaction.kushkiTransferId` = ID en el dashboard de Kushki
- `TreasuryMovement.referenceId` = `payoutId`
- Logs con `[PAYOUT-SCHEDULER]`, `[KUSHKI WEBHOOK]` como prefijos filtrables
