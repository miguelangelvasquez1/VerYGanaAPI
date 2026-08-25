# Sistema de Payouts — Documentación Técnica

## Índice

1. [Visión general](#1-visión-general)
2. [Arquitectura del sistema](#2-arquitectura-del-sistema)
3. [Modelos de datos](#3-modelos-de-datos)
4. [Flujo completo de un payout](#4-flujo-completo-de-un-payout)
5. [Reclamo de productos: qué hace elegible a un ítem para el payout](#5-reclamo-de-productos-qué-hace-elegible-a-un-ítem-para-el-payout)
6. [Disputas y reembolsos](#6-disputas-y-reembolsos)
7. [Métodos de pago de los comercials](#7-métodos-de-pago-de-los-comercials)
8. [Integración con Wompi Pagos a Terceros](#8-integración-con-wompi-pagos-a-terceros)
9. [Job scheduler](#9-job-scheduler)
10. [Webhook de confirmación](#10-webhook-de-confirmación)
11. [Tesorería y movimientos contables](#11-tesorería-y-movimientos-contables)
12. [Configuración](#12-configuración)
13. [Endpoints de la API](#13-endpoints-de-la-api)
14. [Operación y monitoreo](#14-operación-y-monitoreo)
15. [Manejo de errores y reintentos](#15-manejo-de-errores-y-reintentos)

---

## 1. Visión general

El sistema de payouts transfiere diariamente a cada comercial los ingresos netos generados por los productos que sus compradores **ya reclamaron**.

**Un solo proveedor, dos productos:**

| Operación | Pasarela |
|---|---|
| Cobros a consumidores (Copayments, Subscriptions, Investments) | **Wompi — Checkout/Transacciones** |
| Desembolsos a comercials | **Wompi — Pagos a Terceros** |

> El proveedor de payouts fue originalmente Kushki. Kushki rechazó la alianza para el producto de payouts, así que el sistema migró a Wompi Pagos a Terceros — el mismo proveedor que ya se usaba para cobros. Esto elimina una integración externa completa y simplifica la conciliación (un solo dashboard, una sola relación comercial).

**Principio central (revisado):** un comercial **no cobra por vender** — cobra por **entregar**. El pago se paga solo la porción de sus ventas cuyo `PurchaseItem` llegó a estado `CLAIMED` (código digital entregado automáticamente, o código físico recogido y confirmado con PIN — ver [sección 5](#5-reclamo-de-productos-qué-hace-elegible-a-un-ítem-para-el-payout)). Un comercial puede tener 100 ventas en un día y que solo 50 se hayan reclamado: el payout de ese día cubre esas 50, y las otras 50 entran al payout del día en que efectivamente se reclamen — sin fecha límite ni pérdida.

**Ciclo de vida de un payout:**

```
Ítems reclamados (PurchaseItem.status = CLAIMED, sin PayoutItem, sin PQRS abierto)
        ↓
  scheduleDailyPayouts()          [11:00 PM Colombia]
  → agrupa ítems reclamados por commercial
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

**Frecuencia:** una vez al día. Un ítem reclamado espera máximo 24 horas para entrar al ciclo de payout de su comercial.

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
  PayoutItem.java                  → línea individual: un PurchaseItem reclamado dentro de un payout
  PurchaseItemCashRefund.java      → reembolso en efectivo pendiente de pago manual (ver sección 6)

models/marketplace/
  Product.java                     → +campo fulfillmentType (ProductType: DIGITAL | PHYSICAL)
  PurchaseItem.java                → +campos de reclamo físico (claimPinHash, claimAttempts,
                                      claimedAt, claimExpiresAt) — ver sección 5

models/enums/marketplace/
  ProductType.java                 → DIGITAL | PHYSICAL
  PurchaseItemStatus.java          → PENDING | CLAIMED | EXPIRED_UNCLAIMED | REFUNDED | CANCELLED

models/enums/finance/
  PayoutStatus.java                → SCHEDULED | PROCESSING | PAID | FAILED | EXHAUSTED
  CashRefundStatus.java            → PENDING_PAYMENT | PAID
  WompiTransactionType.java        → CHARGE_* | TRANSFER_PAYOUT
  WompiTransactionStatus.java      → PENDING | APPROVED | DECLINED | ERROR | VOIDED
  MovementConcept.java             → +COMMISSION_REVERSAL, REFUND_KEYS_TO_RESERVE,
                                      REFUND_CASH_TO_OPERATIONS (ver sección 11)

services/finance/
  PayoutServiceImpl.java           → lógica de negocio del batch diario
  TreasuryServiceImpl.java         → +reversePurchaseItemForRefund, +registerManualCashRefundPaid
  CashRefundServiceImpl.java       → flujo de reembolso manual en efectivo

services/marketplace/
  PurchaseItemServiceImpl.java     → +claimPhysicalItem, +getReportableItem
  PurchaseItemRefundServiceImpl.java → +refund (disputa PQRS), +expireUnclaimed (vencimiento)

schedulers/
  PayoutScheduler.java              → @Scheduled cron del batch de payouts
  PurchaseItemExpirationScheduler.java → @Scheduled cron: vence ítems físicos no reclamados

services/wompi/
  WompiClient.java                 → HTTP client de cobros
  WompiPayoutClient.java           → HTTP client de Pagos a Terceros

controllers/wompi/
  WompiWebhookController.java        → endpoint POST /wompi/events (cobros)
  WompiWebhookDispatcher.java        → enruta eventos de cobros por tipo
  WompiPayoutWebhookController.java  → endpoint POST /wompi/payouts/events

controllers/marketplace/
  PurchaseItemController.java      → /purchaseItems/{id}/claim, /report, /cash-refund/bank-details

controllers/admin/
  CashRefundAdminController.java   → /admin/cash-refunds

repositories/finance/
  PayoutRepository.java
  WompiTransactionRepository.java
  PayoutMethodRepository.java
  PurchaseItemCashRefundRepository.java

repositories/marketplace/
  PurchaseItemRepository.java      → +findClaimedWithoutPayout, +findExpiredUnclaimedPhysicalItems

dtos/wompi/
  WompiPayoutRequestDTO.java        → body de POST /payouts
  WompiPayoutResponseDTO.java
  WompiPayoutBalanceResponseDTO.java → respuesta de GET /accounts
  WompiPayoutWebhookEvent.java       → payload del webhook de Pagos a Terceros

dtos/finance/
  requests/SubmitCashRefundBankDetailsRequestDTO.java
  responses/CashRefundResponseDTO.java
```

---

## 3. Modelos de datos

### 3.1 Payout

Representa el pago diario batch a un comercial. Uno por comercial por día (por lo que efectivamente entró al batch — ver sección 5).

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | UUID | PK inmutable |
| `commercial` | CommercialDetails | empresario receptor |
| `grossAmountCents` | Long | suma de `subtotalCents` de los ítems reclamados incluidos |
| `commissionCents` | Long | parte que retiene VeryGana |
| `netAmountCents` | Long | `gross - commission` — lo que recibe el commercial |
| `commissionPctApplied` | Integer | snapshot del % aplicado en este payout (auditoría) |
| `status` | PayoutStatus | estado actual del pago |
| `wompiTransaction` | WompiTransaction | FK — se vincula cuando el job pasa a PROCESSING |
| `scheduledAt` | ZonedDateTime | cuándo creó el job este payout |
| `paidAt` | ZonedDateTime | cuándo Wompi confirmó el pago |
| `periodStart` / `periodEnd` | ZonedDateTime | **solo informativo** — cuándo corrió el batch, ya no filtra qué ventas entran (ver sección 5) |
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

Línea de detalle dentro de un Payout. **Un PayoutItem = un PurchaseItem reclamado** — no un Copayment completo, porque un mismo Copayment (una sola compra, potencialmente con varios productos) puede tener ítems que se reclaman en días distintos.

| Campo | Tipo | Descripción |
|---|---|---|
| `payout` | Payout | FK al payout del día |
| `purchaseItem` | PurchaseItem | FK al ítem reclamado que financia esta línea — `unique=true`: un ítem solo puede entrar a un payout una vez (idempotencia) |
| `amountCents` | Long | `purchaseItem.netToCommercialCents` en el momento de crear el payout |

### 3.5 PurchaseItem (campos relevantes para payout/reclamo)

| Campo | Tipo | Descripción |
|---|---|---|
| `status` | PurchaseItemStatus | `PENDING` → `CLAIMED` (feliz) / `EXPIRED_UNCLAIMED` / `REFUNDED` / `CANCELLED` — ver sección 5 |
| `deliveredCode` | String (cifrado) | código entregado, siempre al momento de la compra (digital o físico) |
| `deliveredAt` | ZonedDateTime | cuándo se generó/envió el código — **no** cuándo se reclamó |
| `claimPinHash` | String | hash BCrypt del PIN de reclamación física (null si es digital) |
| `claimAttempts` | Integer | intentos fallidos de PIN — bloquea a las 5 |
| `claimedAt` | ZonedDateTime | cuándo pasó a `CLAIMED` (digital: mismo instante que `deliveredAt`; físico: cuando el comerciante valida el PIN) |
| `claimExpiresAt` | ZonedDateTime | solo físico: plazo para reclamar antes de `EXPIRED_UNCLAIMED` |

### 3.6 PurchaseItemCashRefund

Reembolso en efectivo pendiente de pago manual — ver [sección 6](#6-disputas-y-reembolsos).

| Campo | Tipo | Descripción |
|---|---|---|
| `purchaseItem` | PurchaseItem | FK único — el ítem reembolsado que originó este pago |
| `pqrs` | Pqrs | **nullable** — solo si este reembolso nació de resolver un PQRS de marketplace con `action=REFUND` (null si fue un vencimiento automático). Mientras el pago siga pendiente, ese PQRS queda `PENDIENTE_PAGO_REEMBOLSO`; `CashRefundService.markPaid` lo resuelve al confirmar el pago — ver `PQRS_SYSTEM.md` |
| `amountCents` | Long | porción en efectivo a reembolsar (excluye lo ya devuelto como llaves) |
| `accountHolderName/Doc/DocType`, `bankName`, `accountNumber`, `accountType` | — | datos bancarios, null hasta que el comprador los indique |
| `status` | CashRefundStatus | `PENDING_PAYMENT` → `PAID` |
| `paidByAdmin` | AdminDetails | quién confirmó la transferencia manual |
| `paidAt` | ZonedDateTime | cuándo se confirmó |

---

## 4. Flujo completo de un payout

### Fase 1 — scheduleDailyPayouts()

Se ejecuta a las 11 PM Colombia (04:00 UTC).

```
1. Buscar PurchaseItem(status=CLAIMED) sin PayoutItem asociado y sin PQRS abierto
   (PurchaseItemRepository.findClaimedWithoutPayout — sin filtro de fecha)
2. Agrupar por commercial_id (product.commercial)
3. Por cada grupo:
   a. grossAmountCents  = Σ item.subtotalCents
   b. commissionCents   = Σ item.commissionCents
   c. netAmountCents    = Σ item.netToCommercialCents
   d. commissionPctApplied = snapshot del % del plan actual (último ítem procesado)
   e. Crear Payout(status=SCHEDULED)
   f. Crear un PayoutItem por cada PurchaseItem del grupo
4. Idempotencia: la unique constraint en PayoutItem.purchase_item_id impide
   que el mismo ítem entre a un payout dos veces
```

**Por qué por ítem reclamado y no por Copayment completo:** un comprador puede pagar 3 productos en una sola transacción (un Copayment), pero si uno es digital (se reclama al instante) y dos son físicos (se reclaman cuando el comprador pasa por la tienda), cada uno puede entrar al payout del comercial en un día distinto. Agrupar por Copayment habría forzado a esperar a que **todos** los ítems de la compra estuvieran reclamados para pagar **cualquiera** de ellos.

**Por qué batch y no en tiempo real:**
- El batch agrupa todos los ítems reclamados del día en 1 transferencia por comercial.
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

## 5. Reclamo de productos: qué hace elegible a un ítem para el payout

### 5.1 Digital vs. físico

Cada `Product` tiene `fulfillmentType` (`ProductType.DIGITAL | PHYSICAL`, default `DIGITAL`). Determina qué pasa al momento de la entrega (`CopaymentServiceImpl.deliverProducts()`, disparado cuando Wompi aprueba el copago):

```
DIGITAL:
  código entregado (deliveredCode, deliveredAt)
  → status = CLAIMED de inmediato (claimedAt = deliveredAt)
  → el proceso de redención ocurre fuera de la plataforma (Spotify, Netflix, etc.),
    VerYGana no tiene más interacción con ese ítem

PHYSICAL:
  código entregado (deliveredCode, deliveredAt)
  → se genera un PIN de 6 dígitos, se hashea (claimPinHash) y se envía
    SOLO al comprador (mismo correo de confirmación de compra, junto al código)
  → claimExpiresAt = ahora + marketplace.claim.expiration-days (default 15)
  → status permanece PENDING — "esperando acción del comerciante"
```

El PIN nunca lo ve el comerciante por su cuenta: el comprador se lo entrega de viva voz al recoger el producto en tienda, alineando el incentivo (el comerciante solo cobra si el comprador coopera).

### 5.2 Reclamo físico

`POST /purchaseItems/{id}/claim` (rol `COMMERCIAL`) — `PurchaseItemServiceImpl.claimPhysicalItem`:

```
1. Validar que el ítem pertenezca a un producto de ese comerciante
2. Idempotente: si ya está CLAIMED, responde 200 sin reprocesar
3. Validar que el producto sea PHYSICAL y el ítem esté PENDING
4. Validar que no haya vencido (claimExpiresAt)
5. Validar intentos (máx. 5 — InvalidClaimException si se agotan)
6. Comparar el PIN recibido contra claimPinHash (BCrypt)
   → si no coincide: claimAttempts++, InvalidClaimException
   → si coincide: status = CLAIMED, claimedAt = NOW()
```

### 5.3 Elegibilidad para el payout

`PurchaseItemRepository.findClaimedWithoutPayout()`:

```sql
PurchaseItem.status = CLAIMED
AND NOT EXISTS (PayoutItem para este ítem)
AND NOT EXISTS (Pqrs vinculado a este ítem con status NOT IN (RESUELTA, CERRADA))
```

Sin filtro de fecha: un ítem reclamado 10 días después de la venta entra al payout del día en que se reclamó, no al de la venta ni se pierde. Ver [sección 6](#6-disputas-y-reembolsos) para el caso en que hay una disputa abierta.

### 5.4 Vencimiento automático (ítems físicos nunca reclamados)

`PurchaseItemExpirationScheduler` — corre diario (`marketplace.claim.expiration-scheduler.cron`, default `0 0 5 * * *` = 5 AM UTC):

```
1. PurchaseItemRepository.findExpiredUnclaimedPhysicalItems(now)
   → PurchaseItem(status=PENDING, product.fulfillmentType=PHYSICAL, claimExpiresAt < now)
2. Por cada ítem (aislado — uno que falle no detiene a los demás):
   a. PurchaseItemRefundService.expireUnclaimed(item) → ver sección 6
   b. EmailService.sendPhysicalItemExpiredToConsumer(item, ...)
   c. EmailService.sendPhysicalItemExpiredToCommercial(item)
```

Un ítem que expira **nunca llegó a CLAIMED**, así que nunca estuvo en riesgo de pagarse — el vencimiento solo dispara su reembolso (ver sección 6), no afecta ningún payout ya calculado.

---

## 6. Disputas y reembolsos

Dos caminos disparan un reembolso de `PurchaseItem`, ambos manejados por `PurchaseItemRefundService`:

| Camino | Método | Termina en | Quién lo dispara |
|---|---|---|---|
| Comprador reporta un problema (código inválido, no entregado, no corresponde) | `refund(item, reason, pqrs)` | `REFUNDED` | Admin, al resolver el PQRS vinculado con acción `REFUND` |
| Ítem físico nunca reclamado dentro del plazo | `expireUnclaimed(item)` | `EXPIRED_UNCLAIMED` | `PurchaseItemExpirationScheduler`, automático |

Ambos comparten la misma mecánica financiera (`reverseFinancials()`); solo difieren en el estado final y en si el stock se invalida.

### 6.1 Disputas (PQRS vinculado a un ítem)

`POST /purchaseItems/{id}/report` (rol `CONSUMER`) crea un `Pqrs(type=RECLAMO)` vinculado al `PurchaseItem` (`Pqrs.purchaseItem`, `Pqrs.reasonCode`: `CODE_INVALID | NOT_DELIVERED | NOT_AS_DESCRIBED | OTHER`). Reutiliza el flujo normal de asignación/SLA de PQRS (ver `PQRS_SYSTEM.md`) sin cambios.

**Mientras ese PQRS esté abierto** (no `RESUELTA`/`CERRADA` — esto incluye `PENDIENTE_PAGO_REEMBOLSO`), el ítem queda excluido del payout aunque ya esté `CLAIMED` — ver `findClaimedWithoutPayout()` en la sección 5.3.

El admin resuelve con `PATCH /admin/pqrs/{id}/respond`, body con `action`:
- `DISMISS` — el ítem sigue su curso normal (vuelve a ser elegible para payout si estaba `CLAIMED`).
- `REFUND` — dispara `PurchaseItemRefundService.refund(item, pqrs.reasonCode, pqrs)`. Si resulta en un `PurchaseItemCashRefund` (porción en efectivo), el PQRS **no** pasa a `RESUELTA` todavía — queda `PENDIENTE_PAGO_REEMBOLSO` hasta que el admin confirme el pago manual (sección 6.4) — así que el ítem sigue excluido del payout hasta ese momento, aunque el reembolso interno ya se haya ejecutado.

### 6.2 Vencimiento automático

Ver sección 5.4. `expireUnclaimed(item)` no requiere PQRS — es una regla determinística, sin juicio humano de por medio.

### 6.3 Mecánica financiera compartida (`reverseFinancials`)

El dinero de un ítem con disputa o vencido **nunca llegó a pagársele al comerciante** (el payout solo toma ítems `CLAIMED` sin disputa), así que reversar no requiere tocar Wompi:

```
itemTotalCents = item.commissionCents + item.netToCommercialCents
keysPortionCents = proporción del ítem pagada con llaves
                  ≈ copayment.keysValueCents × (item.subtotalCents / purchase.totalCents)
                  (aproximación: el split llaves/efectivo solo se registra a nivel de
                   Copayment completo, no por ítem — mismo principio que ya usa el
                   sistema para el snapshot de comisión por ítem)
cashPortionCents = itemTotalCents - keysPortionCents

TreasuryService.reversePurchaseItemForRefund(commissionCents, keysPortionCents, cashPortionCents, copaymentId):
  1. commissionCents > 0  → OPERATIONS → PAYOUTS_PENDING       (COMMISSION_REVERSAL)
  2. keysPortionCents > 0 → PAYOUTS_PENDING → KEYS_RESERVE      (REFUND_KEYS_TO_RESERVE)
  3. cashPortionCents > 0 → PAYOUTS_PENDING → OPERATIONS        (REFUND_CASH_TO_OPERATIONS)

Si keysPortionCents > 0:
  → KeyWallet.creditKeysCents(keysPortionCents, 0)
  → KeyTransaction(type=CREDIT_COPAYMENT_REFUND)

Si cashPortionCents > 0:
  → crea PurchaseItemCashRefund(amountCents=cashPortionCents, status=PENDING_PAYMENT)
```

**Por qué la porción en llaves vuelve a `KEYS_RESERVE` y no a `OPERATIONS`:** `KEYS_RESERVE` respalda todas las llaves en circulación. Si se le acreditan llaves de vuelta al comprador sin reponer el fondo, `KEYS_RESERVE` queda desfondeado frente a un pasivo (las llaves) que sigue existiendo.

### 6.4 Reembolso en efectivo — manual, con trazabilidad

Wompi no documenta un endpoint de reverso de cargos (`https://docs.wompi.co/docs/colombia/inicio-rapido/` no tiene una sección de refunds), así que el reembolso en efectivo **no se automatiza**: VerYGana lo paga por fuera de la app (transferencia bancaria propia) y dos endpoints dejan la trazabilidad completa:

```
1. POST /purchaseItems/{id}/cash-refund/bank-details   [CONSUMER]
   El comprador indica a qué cuenta quiere la transferencia.
   CashRefundService.submitBankDetails — valida dueño + que no esté ya PAID.

2. GET /admin/cash-refunds?status=PENDING_PAYMENT       [ADMIN]
   Panel de reembolsos pendientes de transferencia manual.

3. PATCH /admin/cash-refunds/{id}/mark-paid             [ADMIN]
   El admin confirma que ya transfirió por fuera de la app.
   CashRefundService.markPaid — exige que ya haya datos bancarios.
   → TreasuryService.registerManualCashRefundPaid(amountCents, cashRefundId)
     OPERATIONS → EXTERNAL_INCOME (concepto REFUND_TO_BUYER)
   → PurchaseItemCashRefund(status=PAID, paidByAdmin, paidAt)
   → si PurchaseItemCashRefund.pqrs != null, resuelve ese PQRS
     (status=RESUELTA, resolvedAt=now, notifica al solicitante) — ver PQRS_SYSTEM.md
```

**Nota:** `POST /purchaseItems/{id}/cash-refund/bank-details` recibe un `@RequestBody` JSON — el request debe enviarse con `Content-Type: application/json`. Enviarlo como `application/x-www-form-urlencoded` (error típico si el frontend usa un `<form>` o `URLSearchParams` en vez de `JSON.stringify` + fetch/axios con el header correcto) produce un `415 Unsupported Media Type` (antes de esto se devolvía un 500 genérico — `GlobalExceptionHandler` ya mapea `HttpMediaTypeNotSupportedException` explícitamente).

**Vínculo con el PQRS que originó el reembolso:** cuando este `PurchaseItemCashRefund` nació de resolver un PQRS de marketplace con `action=REFUND` (no de un vencimiento automático), queda enlazado a ese PQRS (`PurchaseItemCashRefund.pqrs`). Mientras el pago siga pendiente, el PQRS se queda en `PENDIENTE_PAGO_REEMBOLSO` (no `RESUELTA`) — recién se cierra en el paso 3 de arriba, cuando el admin confirma el pago. Así todo el flujo (aprobación → datos bancarios → pago → cierre) vive dentro del mismo PQRS en vez de partirse en dos objetos sin relación visible para el comprador. Ver `PQRS_SYSTEM.md` sección 3.3 y Fase 3.

### 6.5 Stock al reembolsar

El código (`deliveredCode`) siempre se muestra al comprador desde el momento de la compra, sin importar el estado del ítem — así que el stock **nunca vuelve a `AVAILABLE`** en un reembolso (reutilizar el mismo código para otro comprador filtraría el secreto ya revelado):

- `refund(item, reason=CODE_INVALID)` → `ProductStock.markAsInvalid()` (excluido del inventario).
- `refund(item, cualquier otro motivo)` → el stock queda tal cual (`SOLD`); el comerciante repone stock nuevo.
- `expireUnclaimed(item)` → el stock **nunca** se marca `INVALID` — el código nunca estuvo mal, el comprador simplemente no lo reclamó.

---

## 7. Métodos de pago de los comercials

### Registro de un método

`POST /commercial/payout-methods` — el commercial registra su cuenta.

Dependiendo del tipo:

**BANK_TRANSFER** — requiere: `bankCode` (bankId de Wompi), `accountNumber`, `bankAccountType`, `accountHolderName`, `accountHolderDoc`, `accountHolderDocType`, `alias`.

**NEQUI / DAVIPLATA** — requiere: `phoneNumber`, `accountHolderName`, `accountHolderDoc`, `alias`. El `bankId` de Wompi para estos dos canales no lo elige el commercial — es una constante configurada (`wompi.payout.nequi-bank-id` / `wompi.payout.daviplata-bank-id`).

### Verificación BANK_TRANSFER

1. El commercial registra la cuenta → `PENDING_VERIFICATION`
2. Un admin revisa desde `GET /admin/payout-methods?status=UNDER_REVIEW`
3. Admin aprueba → `VERIFIED` | Admin rechaza → `REJECTED` con razón

### Verificación NEQUI / DAVIPLATA (automática)

1. El commercial registra el número → `PENDING_VERIFICATION`
2. Sistema envía OTP vía Twilio Verify al número registrado → `AWAITING_OTP`
3. Commercial confirma el OTP en la app → `VERIFIED`
4. Si agota los intentos → `REJECTED`

### Regla de primer pago (antifraude)

El flag `firstPayoutCompleted` empieza en `false`. Cuando el job ejecuta el primer payout exitoso al método, lo marca `true`. Hasta que esto ocurra, el job retiene el pago 24h adicionales para revisión antifraude (comportamiento estilo Airbnb).

---

## 8. Integración con Wompi Pagos a Terceros

### Credenciales

| Variable | Descripción |
|---|---|
| `WOMPI_PAYOUT_API_KEY` | Autenticación, header `x-api-key` del WebClient de payouts |
| `WOMPI_PAYOUT_PRINCIPAL_USER_ID` | ID Usuario Principal, header `user-principal-id` |
| `WOMPI_PAYOUT_USER_ID` | Header `user-id` (ver nota abajo — descubierto necesario en pruebas propias, no está en el spec público) |
| `WOMPI_PAYOUT_EVENTS_KEY` | Secreto para validar la firma del webhook de payouts |
| `WOMPI_PAYOUT_ACCOUNT_ID` | Cuenta de origen de las dispersiones (`GET /accounts`) |
| `WOMPI_PAYOUT_NEQUI_BANK_ID` | `bankId` de Wompi que representa a Nequi en el catálogo `/banks` |
| `WOMPI_PAYOUT_DAVIPLATA_BANK_ID` | `bankId` de Wompi que representa a Daviplata |

### Headers de autenticación (confirmados)

Contra el spec público de SwaggerHub (`https://app.swaggerhub.com/apis-docs/wompi/Payouts/1.0.0`), cada request lleva estos headers en minúscula, sin esquema `Bearer`:

```
x-api-key: {WOMPI_PAYOUT_API_KEY}
user-principal-id: {WOMPI_PAYOUT_PRINCIPAL_USER_ID}
user-id: {WOMPI_PAYOUT_USER_ID}
```

`user-id` **no está documentado en el spec público de SwaggerHub** — se agregó porque las pruebas propias en sandbox lo necesitaron para autenticar correctamente (mismo patrón que ya pasó antes con `idempotency-key`: el spec público no es 100% confiable, hay que validar contra el comportamiento real). En la cuenta de prueba, su valor coincide con `WOMPI_PAYOUT_PRINCIPAL_USER_ID`. Ver `WompiPayoutWebClientConfig`.

**`POST /payouts` además requiere un cuarto header, `idempotency-key`** (confirmado en sandbox: sin él, la API responde `500 EXC_001` genérico en vez de un error claro). Debe ser único por request, 1-64 caracteres (letras, números, guion), y expira en 24h — por eso no puede ser un header fijo del `WebClient` como los otros tres; `WompiPayoutClient.createPayout()` genera un `UUID.randomUUID()` nuevo en cada llamada. Ref: `https://docs.wompi.co/docs/colombia/crea-tu-primer-lote/`.

> **Confirmado:** `GET /banks` responde correctamente con las credenciales de sandbox del usuario y el envelope coincide con lo asumido en `WompiPayoutBankResponseDTO`.

### Validaciones en el registro de métodos de pago

`PayoutMethodServiceImpl` valida todo lo que se puede validar en el momento del registro, para no descubrir datos inválidos recién el día del payout:

- **`GET /commercial/payout-methods/banks`** (nuevo, `PayoutMethodController`): expone el catálogo real de `GET /banks` de Wompi para que el frontend deje elegir el `bankCode` correcto en vez de que el commercial lo escriba a mano.
- **Cross-check de `bankCode` contra el catálogo real** al registrar un método `BANK_TRANSFER`: si el `bankCode` enviado no existe en `GET /banks`, el registro se rechaza con 400 en vez de quedar `UNDER_REVIEW` con un dato que Wompi rechazará después.
- **Validación de formato de `accountHolderDoc`** según `accountHolderDocType` (CC/CE/TI/DNI: solo dígitos 5-15; NIT: dígitos 6-12 con dígito de verificación opcional; PP: alfanumérico 5-20). Antes no había ninguna validación de formato — un documento mal escrito solo se detectaba al ejecutar el payout.
- **Screening SARLAFT/OFAC también en NEQUI/DAVIPLATA**: antes `verifyOtp()` marcaba el método `VERIFIED` con solo el OTP de Twilio, sin pasar por `screeningService.screenOrThrow(...)` como sí ocurre en `adminVerifyMethod()` para BANK_TRANSFER. Ahora ambos flujos aplican el mismo screening antes de dejar un método listo para recibir dinero.

**Confirmado — `POST /payouts` exitoso en sandbox (Postman):** con el header `user-id` agregado, transacciones armadas con exactamente los campos de `WompiPayoutTransactionDTO` (`legalIdType`, `legalId`, `personType`, `bankId`, `accountType`, `accountNumber`, `name`, `email`, `amount`, `reference`) fueron aceptadas. Se probó explícitamente **sin** `description`, `phone` (a nivel de transacción) ni `transactionStatus` (a nivel de lote) — los tres son opcionales/no requeridos, no hace falta agregarlos al DTO. Los campos obligatorios según la documentación oficial de SwaggerHub (`legalIdType`, `legalId`, `bankId`, `accountType`, `accountNumber`, `name`, `email`, `amount`, `reference`, `accountId`, `paymentType`) ya están todos cubiertos por el request actual.

`amount` confirmado en **centavos** (la documentación oficial lo especifica explícitamente: "$10,000 COP se representa como 1000000"), consistente con lo que ya se implementaba.

**Sigue pendiente:**
- Confirmar que `DocType.DNI` sea un `legalIdType` aceptado por Wompi (el único ejemplo confirmado usa `CC`).
- Probar el webhook `transaction.updated` end-to-end (que el `Payout` efectivamente pase a `PAID` cuando Wompi confirma).

**Refunds de cobros (Checkout/Transacciones):** tampoco se investigó ni se automatizó — `docs.wompi.co/docs/colombia/inicio-rapido/` no documenta un endpoint de reverso de cargos. Por eso el reembolso en efectivo a un comprador se resuelve manualmente (ver [sección 6.4](#64-reembolso-en-efectivo--manual-con-trazabilidad)) en vez de vía Wompi. Si en el futuro se confirma que sí existe (soporte de Wompi, sandbox), `CashRefundService.markPaid` es el punto natural para reemplazar la confirmación manual por una llamada real a la API.

### Ambientes

| Ambiente | Base URL |
|---|---|
| Sandbox | `https://api.sandbox.payouts.wompi.co/v1` |
| Producción | `https://api.payouts.wompi.co/v1` |

Cambiar en `application-dev.yml` / `application-prod.yml` bajo la clave `wompi.payout.api-base-url`.

### Endpoints usados

| Método | Path | Propósito |
|---|---|---|
| `GET` | `/accounts` | Consultar balance disponible (`data[].balanceInCents`) |
| `POST` | `/payouts` | Crear el payout — sin tokenización previa, todo en una llamada |
| `GET` | `/payouts/{id}` | Consultar estado de un payout (uso manual/soporte) |
| `GET` | `/banks` | Catálogo de bancos/canales disponibles (incluye, a confirmar, Nequi/Daviplata) |

### Body de POST /payouts (confirmado)

```json
{
  "reference": "VG-PAYOUT-{payoutId}",
  "accountId": "{WOMPI_PAYOUT_ACCOUNT_ID}",
  "paymentType": "PROVIDERS",
  "transactions": [
    {
      "legalIdType": "CC",
      "legalId": "123456789",
      "personType": "NATURAL",
      "bankId": "{bankId}",
      "accountType": "AHORROS",
      "accountNumber": "0011223344",
      "name": "Juan Pérez",
      "email": "juan@ejemplo.com",
      "amount": 90000,
      "reference": "VG-PAYOUT-{payoutId}"
    }
  ]
}
```

- `paymentType`: `PAYROLL` | `PROVIDERS` | `OTHER`. Usamos `PROVIDERS` — el payout es una liquidación a un tercero (comercial) por venta, no una nómina.
- `personType`: `NATURAL` | `JURIDICA`, obligatorio. Se infiere en `PayoutServiceImpl`: `JURIDICA` si `legalIdType=NIT`, `NATURAL` en el resto de casos.
- `accountType`: **confirmado en sandbox que solo acepta `AHORROS` o `CORRIENTE`** (400 con cualquier otro valor). El spec público de SwaggerHub documenta un tercer valor `DEPOSITO_ELECTRONICO` para Nequi/Daviplata que en la práctica la API rechaza — para esos dos canales también se envía `AHORROS`.

Respuesta:

```json
{
  "status": 201,
  "code": "OK",
  "message": "Solicitud ejecutada correctamente.",
  "data": { "payoutId": "...", "transactions": 1, "success": 1, "failed": 0 }
}
```

Esta respuesta solo confirma que el lote fue *aceptado* — no es el resultado final. Si `data.failed > 0`, la transacción fue rechazada en validación (dato inválido) y el `Payout` pasa directo a `FAILED`, sin esperar webhook. El resultado real (`APPROVED`/`DECLINED`/`FAILED`) llega vía `POST /wompi/payouts/events`.

### Fondeo del balance de la cuenta de dispersión

Wompi ofrece una **"Wompi Cuenta"**: el mismo lugar donde se acumula el dinero de las ventas de "Recibe pagos online" (nuestros Copayments vía Checkout/Transacciones). Esa misma cuenta puede usarse como `accountId` de origen para Pagos a Terceros — como el payout es siempre el *neto* (gross − comisión), el saldo que ya entra por ventas alcanza para cubrir los payouts sin necesidad de fondeo manual adicional, salvo por el margen operativo que se quiera mantener.

Alternativa: vincular una cuenta bancaria propia (Bancolombia, Banco de Occidente, Banco de Bogotá) como cuenta de origen — requiere firma digital vía ZapSign y tarda hasta 3 días hábiles en activarse (más para bancos distintos de Bancolombia). No es necesario para operar; usar la Wompi Cuenta es más simple.

> **`accountId` no está documentado en el dashboard.** Se obtiene llamando `GET /accounts` una vez la autenticación esté funcionando, y tomando el `id` de la cuenta que corresponda (la Wompi Cuenta aparece junto con cualquier cuenta bancaria vinculada).

El `PayoutScheduler` consulta el balance antes de cada ciclo y lanza una advertencia en los logs si está por debajo del umbral configurado (`wompi.payout.min-balance-alert-cents`).

### Ciclos ACH Colombia

Las transferencias a bancos distintos de Bancolombia/Nequi/Bre-B siguen los ciclos ACH del sistema financiero colombiano: solo se ejecutan en días hábiles en horario bancario. Bancolombia, Nequi y Bre-B sí tienen liquidación inmediata según la documentación de Wompi.

---

## 9. Job Scheduler

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

- Las 11 PM da tiempo a que todos los códigos entregados durante el día que ya fueron reclamados queden reflejados (`CLAIMED`) antes de correr el batch.
- No usar medianoche: los webhooks de Wompi (cobros) pueden tardar varios minutos en llegar.

### Log de ejecución

Cada ciclo produce entradas como:

```
[PAYOUT-SCHEDULER] Balance Wompi Payouts OK: 2500000 COP
[PAYOUT-SCHEDULER] Buscando ítems CLAIMED sin payout asociado
[PAYOUT-SCHEDULER] Encontrados 47 ítems reclamados sin payout.
[PAYOUT-SCHEDULER] Payout SCHEDULED: id=..., commercial=Tienda XYZ, net=185000, ítems=12
[PAYOUT-SCHEDULER] Payout → PROCESSING: id=..., wompiId=wp_123...
[PAYOUT-SCHEDULER] Ciclo diario completado.
[PAYOUT-RETRY] Sin payouts FAILED para reintentar.
```

Job aparte, independiente del horario de payouts — `PurchaseItemExpirationScheduler` corre a las 5 AM UTC (`marketplace.claim.expiration-scheduler.cron`) y produce entradas `[CLAIM-EXPIRY]` (ver sección 5.4).

---

## 10. Webhook de confirmación

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

## 11. Tesorería y movimientos contables

### 11.1 Payout pagado

Cuando un payout es `PAID`, el sistema llama a `TreasuryService.registerPayoutSent(netAmountCents, payoutId)`. Esto genera un `TreasuryMovement` del tipo:

```
fromAccount: PAYOUTS_PENDING
toAccount: [cuenta externa del commercial]
concept: PAYOUT_SENT
referenceId: payoutId
```

La comisión de VeryGana fue retenida al momento de cada venta (en `handleApproved()` del servicio de copagos), por lo que `scheduleDailyPayouts()` **no** llama a `retainCommission()` — solo registra el snapshot de `commissionCents` para auditoría del payout.

### 11.2 Reembolso de un PurchaseItem (disputa o vencimiento)

Ver [sección 6.3](#63-mecánica-financiera-compartida-reversefinancials) para el flujo completo. Movimientos generados por `TreasuryService.reversePurchaseItemForRefund`:

| Concepto | from → to | Cuándo |
|---|---|---|
| `COMMISSION_REVERSAL` | OPERATIONS → PAYOUTS_PENDING | siempre que `commissionCents > 0` |
| `REFUND_KEYS_TO_RESERVE` | PAYOUTS_PENDING → KEYS_RESERVE | si el ítem se pagó (parcial o totalmente) con llaves |
| `REFUND_CASH_TO_OPERATIONS` | PAYOUTS_PENDING → OPERATIONS | si el ítem se pagó (parcial o totalmente) en efectivo |

### 11.3 Reembolso en efectivo pagado manualmente

`TreasuryService.registerManualCashRefundPaid(amountCents, cashRefundId)`, disparado por `CashRefundService.markPaid`:

```
fromAccount: OPERATIONS
toAccount: EXTERNAL_INCOME (cuenta externa virtual, dinero sale del banco real)
concept: REFUND_TO_BUYER
referenceId: purchaseItemCashRefundId
```

---

## 12. Configuración

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

marketplace:
  claim:
    expiration-days: 15                  # plazo para reclamar un producto físico (PIN)
    expiration-scheduler:
      cron: "0 0 5 * * *"                # PurchaseItemExpirationScheduler, todos los días 5 AM UTC
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

## 13. Endpoints de la API

### Commercial (rol: `ROLE_COMMERCIAL`)

| Método | Path | Descripción |
|---|---|---|
| `GET` | `/commercial/wallet/me` | Ver balance y estado del wallet |
| `POST` | `/commercial/wallet/withdraw` | Solicitar retiro manual |
| `GET` | `/commercial/wallet/me/transactions` | Historial de depósitos (paginado) |
| `GET` | `/commercial/wallet/me/payouts` | Historial de payouts recibidos (paginado) |
| `POST` | `/purchaseItems/{id}/claim` | Validar el PIN de un ítem físico entregado en persona |

### Consumer (rol: `ROLE_CONSUMER`)

| Método | Path | Descripción |
|---|---|---|
| `POST` | `/purchaseItems/{id}/report` | Reportar un problema con un ítem (crea PQRS vinculado) |
| `POST` | `/purchaseItems/{id}/cash-refund/bank-details` | Indicar la cuenta para un reembolso en efectivo ya aprobado |

### Admin (rol: `ROLE_ADMIN`)

| Método | Path | Descripción |
|---|---|---|
| `GET` | `/admin/payouts?date=YYYY-MM-DD` | Listar payouts de una fecha (hoy si no se pasa) |
| `GET` | `/admin/cash-refunds?status=PENDING_PAYMENT` | Reembolsos en efectivo pendientes de pago manual |
| `PATCH` | `/admin/cash-refunds/{id}/mark-paid` | Confirmar que ya se hizo la transferencia manual |

### Webhook (público)

| Método | Path | Descripción |
|---|---|---|
| `POST` | `/wompi/events` | Confirmaciones de cobros — no requiere JWT |
| `POST` | `/wompi/payouts/events` | Confirmaciones de payouts — no requiere JWT |

---

## 14. Operación y monitoreo

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
GET /admin/payouts?date=2025-03-15
```

Respuesta incluye: `id`, `commercial`, `gross`, `commission`, `net`, `status`, `scheduledAt`, `paidAt`, `failureReason`, `retryCount`.

### Reembolsos en efectivo pendientes

Un `PurchaseItemCashRefund` en `PENDING_PAYMENT` sin datos bancarios todavía es normal (esperando al comprador). Uno con datos bancarios ya cargados y varios días en `PENDING_PAYMENT` es una señal operativa: alguien en soporte/finanzas debe ejecutar la transferencia manual y marcarla pagada. No hay alerta automática todavía — se recomienda revisar `GET /admin/cash-refunds` periódicamente hasta que se justifique automatizarlo.

---

## 15. Manejo de errores y reintentos

### Matriz de fallos

| Escenario | Resultado inmediato | Acción automática |
|---|---|---|
| Commercial sin PayoutMethod verificado | `Payout(FAILED)` | Log WARN — no se reintenta hasta que el commercial verifique un método |
| Wompi `/payouts` falla (timeout, 5xx) | `WompiApiException` capturada → `Payout(FAILED)` | Se reintenta en el ciclo de reintentos (11:30 PM) |
| Wompi `/payouts` devuelve status de rechazo | `Payout(FAILED, failureReason)` | Se reintenta en el ciclo de reintentos |
| Wompi responde 429 (rate limit) | `WompiApiException` (429) con motivo explícito ("rechazó por límite de tasa, no es un rechazo de la transferencia") → `Payout(FAILED)`; `isServerError()` = true (reintentable) | `processScheduledPayouts()`/`retryFailedPayouts()` pausan `wompi.payout.rate-limit-delay-ms` (default 300ms) entre cada llamada consecutiva a Wompi para no provocarlo; si igual ocurre, se reintenta en el ciclo de reintentos |
| Webhook DECLINED/FAILED de Wompi | `Payout(FAILED)` | Se reintenta en el ciclo siguiente |
| Balance de la cuenta de dispersión insuficiente | `PayoutScheduler.checkWompiBalance()` loguea WARN antes del ciclo. Además, `processScheduledPayouts()`/`retryFailedPayouts()` llevan un balance corriente local (arranca del balance real de Wompi, se descuenta por cada payout que sí queda PROCESSING): el primer payout de la lista que ya no alcanza se marca `Payout(FAILED, "Balance insuficiente...")` **sin llamar a Wompi**, igual que el resto de los que le siguen y tampoco alcanzan | Equipo ops recarga el balance; esos payouts se reintentan en el ciclo de reintentos como cualquier otro FAILED |
| Webhook duplicado | Ignorado (idempotencia) | — |
| PIN de reclamo físico incorrecto | `InvalidClaimException` (400), `claimAttempts++` | Bloquea a los 5 intentos |
| `expireUnclaimed`/`refund` fallan para un ítem del batch de vencimiento | Log ERROR, el ítem no se marca | El ítem sigue `PENDING`/en disputa y se reintenta en el próximo ciclo del scheduler correspondiente |

### Límite de reintentos

`retryFailedPayouts()` → `PayoutExecutionService.executeRetry()` tope el número de reintentos con `wompi.payout.max-retries` (default `5`). Al alcanzarlo, el payout pasa a `EXHAUSTED` (sin volver a llamar a Wompi) y deja de ser recogido por `findByStatus(FAILED)` — requiere revisión manual del admin (ej. corregir la cuenta bancaria del empresario) antes de poder reintentarse de nuevo.

### Trazabilidad

Cada payout tiene `id` (UUID) trazable en:
- `Payout.id` → tabla `payouts`
- `WompiTransaction.reference` = `"VG-PAYOUT-{payoutId}"`
- `WompiTransaction.wompiId` = ID en el dashboard de Wompi
- `TreasuryMovement.referenceId` = `payoutId`
- Logs con `[PAYOUT-SCHEDULER]`, `[WOMPI PAYOUT WEBHOOK]` como prefijos filtrables

Cada reembolso es trazable en:
- `PayoutItem.purchaseItem` → qué ítem específico financia cada línea de un payout
- `TreasuryMovement.referenceId` = `copaymentId` (reversión) o `purchaseItemCashRefundId` (pago manual)
- `PurchaseItemCashRefund.paidByAdmin` / `paidAt` → quién y cuándo ejecutó la transferencia manual
- Logs con `[REFUND]` (mecánica financiera) y `[CLAIM-EXPIRY]` (vencimiento automático) como prefijos filtrables
