# Sistema de PQRS — Documentación Técnica

## Índice

1. [Visión general](#1-visión-general)
2. [Arquitectura del sistema](#2-arquitectura-del-sistema)
3. [Modelos de datos](#3-modelos-de-datos)
4. [Asignación equitativa por rotación](#4-asignación-equitativa-por-rotación)
5. [Flujo completo de un PQRS](#5-flujo-completo-de-un-pqrs)
6. [Notificaciones](#6-notificaciones)
7. [Plazos legales (SLA)](#7-plazos-legales-sla)
8. [Jobs programados](#8-jobs-programados)
9. [Endpoints de la API](#9-endpoints-de-la-api)
10. [Configuración](#10-configuración)
11. [Manejo de errores](#11-manejo-de-errores)

---

## 1. Visión general

El sistema de PQRS (Peticiones, Quejas, Reclamos y Sugerencias) permite que cualquier usuario autenticado —sin importar su rol (consumer, commercial, game designer, etc.)— radique una solicitud que llega automáticamente a un administrador para su gestión, tal como lo exige la Ley 1755 de 2015 en Colombia.

**Principio central del diseño:** cada PQRS se asigna por **rotación equitativa** al admin activo que lleva más tiempo esperando su turno. No existe un rol de super-admin ni reasignación manual — el turno de rotación determina, de forma determinística, quién debe resolver cada caso.

**PQRS vinculados a una compra:** además del flujo genérico (`POST /pqrs`), un comprador puede reportar un problema puntual con un ítem de su compra (código inválido, no entregado, no corresponde a lo comprado) desde `POST /purchaseItems/{id}/report`. Esto crea un `Pqrs(type=RECLAMO)` ya vinculado al `PurchaseItem` en cuestión, reutilizando exactamente el mismo motor de asignación/SLA/notificaciones — la única diferencia es que trae un ítem y un motivo (`reasonCode`) adjuntos, y que al resolverlo el admin puede disparar un reembolso. Ver [sección 5](#5-flujo-completo-de-un-pqrs) ("Fase 1b" y "Fase 3") y `PAYOUT_SYSTEM.md` (sección 6, "Disputas y reembolsos") para el detalle completo del lado financiero.

**Ciclo de vida de un PQRS:**

```
Usuario radica PQRS
        ↓
  createPqrs()
  → calcula fecha límite (días hábiles según tipo)
  → pickNextAdmin()  [rotación con lock pesimista]
  → PENDIENTE_ASIGNACION (si no hay admin activo)
     o RECIBIDA (admin asignado)
        ↓
  markUnderReview()          [admin abre el caso]
  → RECIBIDA → EN_REVISION
        ↓
  respondToPqrs()            [admin responde]
  → EN_REVISION → RESUELTA
    (o → PENDIENTE_PAGO_REEMBOLSO si es un reclamo de marketplace
     con action=REFUND y el reembolso tiene porción en efectivo —
     ver sección 3.3 y Fase 3)
        ↓
  (alertas de SLA si se acerca el vencimiento)
```

---

## 2. Arquitectura del sistema

```
┌────────────────────┐        ┌──────────────────────────────┐
│    PqrsController   │  POST  │        PqrsServiceImpl        │
│      /pqrs           │───────▶│  createPqrs()                │
└────────────────────┘        │  getMyPqrs() / getMyPqrsDetail│
                                │  markUnderReview()            │
┌────────────────────┐  PATCH  │  respondToPqrs()               │
│  PqrsAdminController │───────▶│  retryPendingAssignments()    │
│    /admin/pqrs        │        │  sendSlaAlerts()               │
└────────────────────┘        └───────────┬───────────────────┘
                                            │
                    ┌───────────────────────┼───────────────────────┐
                    ▼                       ▼                       ▼
        ┌──────────────────────┐  ┌──────────────────┐   ┌─────────────────────┐
        │ PqrsAssignmentService │  │   EmailService     │   │  NotificationService │
        │ pickNextAdmin()        │  │ (SendGrid, @Async) │   │ (in-app + SSE)        │
        │ @Lock PESSIMISTIC_WRITE│  └──────────────────┘   └─────────────────────┘
        └──────────────────────┘
```

**Archivos principales:**

```
models/enums/pqrs/
  PqrsType.java                    → PETICION | QUEJA | RECLAMO | SUGERENCIA
  PqrsStatus.java                  → PENDIENTE_ASIGNACION | RECIBIDA | EN_REVISION |
                                      PENDIENTE_PAGO_REEMBOLSO | RESUELTA | CERRADA
  MarketplaceIssueReason.java      → CODE_INVALID | NOT_DELIVERED | NOT_AS_DESCRIBED | OTHER
                                      (solo para PQRS radicados desde /purchaseItems/{id}/report)
  PqrsResolutionAction.java        → DISMISS | REFUND (acción del admin al resolver un PQRS
                                      vinculado a un PurchaseItem)

models/pqrs/
  Pqrs.java                        → entidad principal, +purchaseItem, +reasonCode

models/userDetails/
  AdminDetails.java                → +campo lastPqrsAssignedAt (cursor de rotación)

repositories/pqrs/
  PqrsRepository.java
repositories/details/
  AdminDetailsRepository.java      → +findActiveAdminsForPqrsAssignmentForUpdate()

services/pqrs/
  PqrsServiceImpl.java             → lógica de negocio
  PqrsAssignmentService.java       → rotación round-robin con lock pesimista

services/interfaces/pqrs/
  PqrsService.java

mappers/pqrs/
  PqrsMapper.java                  → MapStruct

utils/pqrs/
  BusinessDayCalculator.java       → suma días hábiles (lunes–viernes)
  RequesterNameResolver.java       → resuelve el nombre a mostrar de un User según su rol

config/pqrs/
  PqrsSlaProperties.java           → @ConfigurationProperties prefix="pqrs"

controllers/pqrs/
  PqrsController.java              → /pqrs (usuario)
controllers/admin/
  PqrsAdminController.java         → /admin/pqrs (admin)
controllers/marketplace/
  PurchaseItemController.java      → POST /purchaseItems/{id}/report (crea el PQRS vinculado)

services/interfaces/marketplace/
  PurchaseItemRefundService.java   → refund(item, reason, pqrs) — disparado por respondToPqrs(action=REFUND).
                                      Retorna el PurchaseItemCashRefund creado (o null si no hubo
                                      porción en efectivo) para que respondToPqrs decida si el PQRS
                                      se resuelve ya o queda PENDIENTE_PAGO_REEMBOLSO.
                                      Ver PAYOUT_SYSTEM.md sección 6.

services/finance/
  CashRefundServiceImpl.java       → markPaid() resuelve el PQRS vinculado (si lo hay) cuando el
                                      admin confirma el pago manual del reembolso — ver Fase 3.

schedulers/
  PqrsAssignmentRetryScheduler.java
  PqrsSlaMonitorScheduler.java

exceptions/pqrsExceptions/
  PqrsAccessDeniedException.java   → 403, mapeada en GlobalExceptionHandler

dtos/pqrs/requests/
  CreatePqrsRequestDTO.java
  RespondPqrsRequestDTO.java
dtos/pqrs/responses/
  PqrsResponseDTO.java             → vista del ciudadano
  PqrsAdminDetailDTO.java          → vista del admin (incluye datos del solicitante)

resources/templates/email/
  pqrs-received-confirmation.html
  pqrs-assigned-admin.html
  pqrs-resolved.html
  pqrs-sla-alert.html
```

---

## 3. Modelos de datos

### 3.1 Pqrs

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | Long | PK autoincremental |
| `type` | PqrsType | `PETICION` \| `QUEJA` \| `RECLAMO` \| `SUGERENCIA` |
| `status` | PqrsStatus | ver ciclo de vida arriba |
| `requester` | User | quien radica — es un `User` genérico, no una entidad de detalle por rol, porque cualquier rol puede radicar un PQRS |
| `assignedAdmin` | AdminDetails | admin al que le tocó por rotación (nullable mientras esté `PENDIENTE_ASIGNACION`) |
| `subject` | String | asunto (máx. 200 caracteres) |
| `description` | String (TEXT) | descripción completa |
| `response` | String (TEXT) | respuesta del admin (nullable hasta resolver) |
| `dueDate` | ZonedDateTime | fecha límite legal, calculada al crear |
| `createdAt` / `updatedAt` / `resolvedAt` | ZonedDateTime | auditoría |
| `purchaseItem` | PurchaseItem | **nullable** — solo se llena cuando el PQRS se radica desde `/purchaseItems/{id}/report` |
| `reasonCode` | MarketplaceIssueReason | **nullable** — motivo específico del reclamo de marketplace (`CODE_INVALID` \| `NOT_DELIVERED` \| `NOT_AS_DESCRIBED` \| `OTHER`) |

Mientras un PQRS con `purchaseItem` vinculado no esté `RESUELTA`/`CERRADA` (esto incluye `PENDIENTE_PAGO_REEMBOLSO`), ese ítem queda excluido del payout diario al comercial — ver `PurchaseItemRepository.findClaimedWithoutPayout()` en `PAYOUT_SYSTEM.md`.

El campo `based` (así se expone en las respuestas JSON, `PqrsResponseDTO.based` / `PqrsAdminDetailDTO.based`) es el **número de radicado**. No se persiste en base de datos: se deriva en `Pqrs.getBased()` a partir del año de creación y el id (`"PQRS-{año}-{id con 6 dígitos}"`), y MapStruct lo mapea automáticamente al DTO por coincidencia de nombre de propiedad.

Métodos de transición en la propia entidad (mismo patrón que `BrandingRequest.canBeX()`):

```java
canBeReviewed()        // true si status == RECIBIDA
canBeResolved()         // true si status == RECIBIDA || EN_REVISION
isPendingAssignment()   // true si status == PENDIENTE_ASIGNACION
```

### 3.2 AdminDetails (campo agregado)

| Campo | Tipo | Descripción |
|---|---|---|
| `lastPqrsAssignedAt` | ZonedDateTime | cursor de rotación — el admin con el valor más antiguo (o `null`) es el siguiente en recibir un PQRS |

### 3.3 MarketplaceIssueReason / PqrsResolutionAction

Solo aplican a PQRS con `purchaseItem` vinculado (radicados desde `/purchaseItems/{id}/report`):

```java
public enum MarketplaceIssueReason {
    CODE_INVALID,      // el código/PIN no funcionó
    NOT_DELIVERED,     // nunca llegó el producto (físico) o el código (digital)
    NOT_AS_DESCRIBED,  // el producto no corresponde a lo comprado
    OTHER
}

public enum PqrsResolutionAction {
    DISMISS,  // el ítem sigue su curso normal (código válido, entrega confirmada)
    REFUND    // dispara PurchaseItemRefundService.refund(item, reasonCode, pqrs)
}
```

`reasonCode` se fija al crear el PQRS (elegido por el comprador) y viaja intacto hasta la resolución — es el mismo valor que recibe `PurchaseItemRefundService.refund()` para decidir si el stock se marca `INVALID` (solo si `CODE_INVALID`).

`Pqrs.action` (nullable) se persiste en el momento de resolver (`respondToPqrs`) — no es solo el campo transitorio de `RespondPqrsRequestDTO`. Se expone en `PqrsResponseDTO`/`PqrsAdminDetailDTO` para que el frontend, leyendo el PQRS, sepa si el admin aprobó `REFUND` y deba mostrarle al comprador el formulario de `POST /purchaseItems/{id}/cash-refund/bank-details` (ver `PAYOUT_SYSTEM.md` sección 6.4) — sin esto, el chat de PQRS del comprador no tendría forma de saber cuándo pedir la cuenta bancaria.

**El PQRS solo se cierra cuando el problema realmente se resolvió, no cuando el admin lo aprobó.** Si `action = REFUND` y el reembolso tiene una porción en efectivo (ver `PurchaseItemCashRefund` en `PAYOUT_SYSTEM.md` sección 6.4), esa porción queda pendiente de un pago manual que el admin confirma después, por fuera de este endpoint. Hasta ese momento el PQRS queda en `PENDIENTE_PAGO_REEMBOLSO` — no `RESUELTA` — para que todo el flujo (aprobación → datos bancarios del comprador → pago → cierre) quede dentro del mismo PQRS en vez de partirse en dos objetos. `PurchaseItemCashRefund.pqrs` es el FK que sostiene ese vínculo; `CashRefundServiceImpl.markPaid()` es quien finalmente pasa el PQRS a `RESUELTA` cuando el admin confirma el pago (ver Fase 3 y `PAYOUT_SYSTEM.md` sección 6.4). Si el reembolso se cubrió 100% con llaves (sin porción en efectivo) o la acción fue `DISMISS`, no hay nada que esperar y el PQRS se resuelve de inmediato, como antes.

---

## 4. Asignación equitativa por rotación

Este es el corazón del sistema. En vez de un puntero fijo a una lista ordenada de admins (frágil ante altas/bajas de personal y ante condiciones de carrera), se usa **"menos asignaciones recientes"**: cada admin activo tiene un timestamp `lastPqrsAssignedAt`, y el siguiente PQRS siempre se le asigna al admin cuyo timestamp sea el más antiguo (los admins que nunca han recibido uno, con `lastPqrsAssignedAt = null`, van primero).

**Query de selección** (`AdminDetailsRepository.findActiveAdminsForPqrsAssignmentForUpdate`):

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM AdminDetails a JOIN a.user u " +
       "WHERE u.role = Role.ADMIN AND u.userState = UserState.ACTIVE " +
       "ORDER BY a.lastPqrsAssignedAt ASC, a.id ASC")
List<AdminDetails> findActiveAdminsForPqrsAssignmentForUpdate(Pageable pageable);
```

**`PqrsAssignmentService.pickNextAdmin()`**:

```java
1. SELECT ... FOR UPDATE LIMIT 1  → admin con el turno más antiguo
2. admin.lastPqrsAssignedAt = now()
3. save(admin)
4. return admin
```

El `PESSIMISTIC_WRITE` bloquea esa fila hasta que la transacción de creación del PQRS termina, así que si dos PQRS llegan al mismo tiempo, el segundo espera a que el primero libere el lock — nunca pueden "leer" el mismo admin como candidato y asignárselo dos veces. Al ser pocos admins y una operación mínima, el candado se libera casi instantáneamente y no genera cuello de botella.

**Casos borde:**

- **Sin admins activos:** `pickNextAdmin()` devuelve `Optional.empty()`, el PQRS queda `PENDIENTE_ASIGNACION` (no se rechaza la creación) y el scheduler `PqrsAssignmentRetryScheduler` reintenta cada 15 minutos.
- **Admin desactivado:** desaparece automáticamente de la query (filtra por `userState = ACTIVE`), sin romper el orden de los demás.
- **Admin nuevo:** entra con `lastPqrsAssignedAt = null`, que en MySQL ordena primero en `ASC`, así que recibe el próximo turno de inmediato.
- **No hay reasignación manual:** no existe rol de super-admin en la plataforma, así que un PQRS le pertenece únicamente al admin al que le tocó por rotación hasta que lo resuelve.

---

## 5. Flujo completo de un PQRS

### Fase 1 — Creación (`createPqrs`)

```
1. Cargar el User solicitante desde el JWT (claim "userId")
2. Calcular dueDate = hoy + días hábiles según el tipo (BusinessDayCalculator)
3. pickNextAdmin() → asigna admin o deja PENDIENTE_ASIGNACION
4. Guardar el Pqrs
5. Si quedó asignado: notificar al admin (email + in-app)
6. Siempre: enviar confirmación de recepción al solicitante (email, con el radicado)
```

Anotado con `@Auditable(action = "PQRS_SUBMIT", category = "PQRS")` para trazabilidad.

### Fase 1b — Creación vinculada a una compra (`createPqrsForPurchaseItem`)

`PurchaseItemController.reportIssue` (`POST /purchaseItems/{id}/report`) primero valida con `PurchaseItemService.getReportableItem(itemId, consumerId)` que el ítem sea del comprador autenticado, que no esté ya `REFUNDED`/`CANCELLED`, y que si ya está `CLAIMED` esté dentro de una ventana de 48h desde el reclamo (permite reportar "el PIN era correcto pero el producto no correspondía" incluso después de reclamado). Luego llama a `PqrsService.createPqrsForPurchaseItem(item, reason, description, consumerId)`, que comparte toda la lógica de `createAndDispatch()` con `createPqrs()` (misma asignación por rotación, mismo cálculo de `dueDate`, mismas notificaciones) pero:

```
type = RECLAMO (siempre)
subject = generado automáticamente ("Reclamo por compra #{purchaseId} ({reason})")
purchaseItem = el ítem validado
reasonCode = el motivo elegido por el comprador
```

### Fase 2 — Revisión (`markUnderReview`)

El admin dueño del PQRS lo marca como `EN_REVISION` (`RECIBIDA → EN_REVISION`). Si el PQRS no está en `RECIBIDA`, lanza `ValidationException` (400).

### Fase 3 — Resolución (`respondToPqrs`)

```
1. Verificar que el PQRS esté asignado al admin autenticado
   → si no, PqrsAccessDeniedException (403)
2. Verificar canBeResolved() (RECIBIDA o EN_REVISION)
   → si no, ValidationException (400) — esto también bloquea reintentar la
     resolución de un PQRS que ya quedó en PENDIENTE_PAGO_REEMBOLSO
3. Si pqrs.purchaseItem != null (PQRS vinculado a una compra):
   a. dto.action es obligatorio → si viene null, ValidationException (400)
   b. Si action = REFUND → PurchaseItemRefundService.refund(pqrs.purchaseItem, pqrs.reasonCode, pqrs)
      (revierte tesorería, acredita llaves si aplica, crea el reembolso en efectivo
      pendiente si aplica, vinculado a este mismo PQRS — ver PAYOUT_SYSTEM.md sección 6)
   c. Si action = DISMISS → no se ejecuta ninguna acción financiera, el ítem sigue su curso normal
   (para un PQRS genérico sin purchaseItem, action se ignora por completo)
   pqrs.action se persiste en cualquier caso (b o c)
4. Guardar response
5a. Si el paso 3b creó un PurchaseItemCashRefund (porción en efectivo pendiente):
    status = PENDIENTE_PAGO_REEMBOLSO, resolvedAt queda null, NO se notifica
    todavía "PQRS resuelto" — el flujo sigue abierto hasta el paso 6.
5b. En cualquier otro caso (REFUND cubierto 100% con llaves, DISMISS, o PQRS
    genérico sin purchaseItem): status = RESUELTA, resolvedAt = now(), se
    notifica al solicitante (email + in-app) con la respuesta.
6. (Async, fuera de este endpoint) Cuando el admin confirma el pago manual del
   reembolso — PATCH /admin/cash-refunds/{id}/mark-paid, ver PAYOUT_SYSTEM.md
   sección 6.4 — CashRefundServiceImpl.markPaid() detecta el PQRS vinculado
   (PurchaseItemCashRefund.pqrs) y recién ahí lo pasa a RESUELTA + notifica.
```

Anotado con `@Auditable(action = "PQRS_RESPOND", category = "PQRS")`.

**Control de acceso:** cada operación admin (`getPqrsDetailForAdmin`, `markUnderReview`, `respondToPqrs`) pasa por `loadOwnedByAdmin()`, que verifica `pqrs.assignedAdmin.user.id == adminUserId`. Un admin **no puede** actuar sobre un PQRS que no le tocó por rotación.

---

## 6. Notificaciones

Cada evento relevante dispara dos canales en paralelo, llamados de forma síncrona dentro del método `@Transactional` (mismo patrón que `BrandingRequestServiceImpl` — no se usa el patrón outbox):

| Evento | Email (SendGrid, `@Async`) | In-app / SSE |
|---|---|---|
| PQRS creado y asignado | `pqrs-assigned-admin.html` → al admin | `NotificationService.createInternalNotification` → al admin |
| PQRS creado (siempre) | `pqrs-received-confirmation.html` → al solicitante | — |
| PQRS resuelto (`respondToPqrs`, o `CashRefundServiceImpl.markPaid` si quedó `PENDIENTE_PAGO_REEMBOLSO`) | `pqrs-resolved.html` → al solicitante | `createInternalNotification` → al solicitante |
| PQRS por vencer | `pqrs-sla-alert.html` → al admin | — |

Las notificaciones in-app se persisten y además se empujan en tiempo real por SSE (`NotificationEmitterRegistry`) si el admin tiene el panel abierto; el correo llega siempre, esté o no conectado.

El nombre que se muestra en los correos (`requesterName` / `adminName`) se resuelve con `RequesterNameResolver`, que inspecciona el `UserDetails` real del usuario (`ConsumerDetails.name`, `CommercialDetails.companyName`, `GameDesignerDetails.name`, o el email como fallback), ya que el nombre vive en la entidad de detalle específica de cada rol y no en `User`.

---

## 7. Plazos legales (SLA)

Basado en la Ley 1755 de 2015. Configurable en `application.yml` sin tocar código:

```yaml
pqrs:
  sla-days:
    PETICION: 15
    QUEJA: 15
    RECLAMO: 15
    SUGERENCIA: 10
```

`PqrsSlaProperties.getSlaDaysFor(type)` devuelve el valor configurado (o 15 por defecto si el tipo no está en el mapa). `BusinessDayCalculator.addBusinessDays()` suma esos días hábiles (lunes a viernes) a la fecha de creación para obtener `dueDate`.

> **Limitación conocida:** no contempla el calendario de festivos colombianos todavía — solo excluye sábados y domingos. Mejora pendiente, no bloquea el funcionamiento actual.

---

## 8. Jobs programados

### PqrsAssignmentRetryScheduler

```
cron: "0 */15 * * * *"   (cada 15 minutos, configurable: pqrs.assignment-retry.cron)
```

Busca PQRS en `PENDIENTE_ASIGNACION` y reintenta `pickNextAdmin()` para cada uno. Cubre el caso borde de que no hubiera ningún admin activo al momento de la creación.

### PqrsSlaMonitorScheduler

```
cron: "0 0 8 * * *"  UTC   (8 AM, configurable: pqrs.sla-alert.cron)
```

Busca PQRS en `RECIBIDA` o `EN_REVISION` cuya `dueDate` esté a menos de `pqrs.sla-alert.days-before-due-date` días (default: 2) o ya vencida, y envía `pqrs-sla-alert.html` al admin asignado. No cambia el estado del PQRS — solo alerta; cumplir el plazo legal es responsabilidad del admin.

---

## 9. Endpoints de la API

### Usuario (cualquier rol autenticado)

| Método | Path | Descripción |
|---|---|---|
| `POST` | `/pqrs` | Radica un nuevo PQRS |
| `GET` | `/pqrs/mine` | Lista paginada de los PQRS propios |
| `GET` | `/pqrs/{id}` | Detalle de un PQRS propio |

### Consumer (rol `ROLE_CONSUMER`)

| Método | Path | Descripción |
|---|---|---|
| `POST` | `/purchaseItems/{id}/report` | Radica un PQRS (`RECLAMO`) ya vinculado a un ítem de compra — ver Fase 1b |

### Admin (rol `ROLE_ADMIN`)

| Método | Path | Descripción |
|---|---|---|
| `GET` | `/admin/pqrs?status=&type=` | PQRS asignados al admin autenticado (filtros opcionales) |
| `GET` | `/admin/pqrs/{id}` | Detalle de un PQRS asignado a ese admin |
| `PATCH` | `/admin/pqrs/{id}/review` | Marca el PQRS como `EN_REVISION` |
| `PATCH` | `/admin/pqrs/{id}/respond` | Resuelve el PQRS (body: `{ "response": "...", "action": "DISMISS" \| "REFUND" }` — `action` solo obligatorio si el PQRS tiene `purchaseItem` vinculado). Si `action=REFUND` deja porción en efectivo pendiente, el PQRS queda `PENDIENTE_PAGO_REEMBOLSO` en vez de `RESUELTA` — ver Fase 3 y `PAYOUT_SYSTEM.md` sección 6.4 para el endpoint que lo cierra (`PATCH /admin/cash-refunds/{id}/mark-paid`) |

No existe un endpoint "de todos los PQRS" — cada admin solo ve los que le tocaron por rotación, ya que no hay rol de super-admin en la plataforma.

---

## 10. Configuración

```yaml
pqrs:
  sla-days:
    PETICION: 15
    QUEJA: 15
    RECLAMO: 15
    SUGERENCIA: 10
  assignment-retry:
    cron: "0 */15 * * * *"        # opcional, default cada 15 min
  sla-alert:
    cron: "0 0 8 * * *"            # opcional, default 8 AM UTC
    days-before-due-date: 2        # opcional, default 2 días
```

No requiere variables de entorno nuevas — reutiliza la configuración existente de SendGrid (`sendgrid.from-email`, `sendgrid.support-email`, etc.) y de notificaciones in-app.

---

## 11. Manejo de errores

| Escenario | Excepción | HTTP |
|---|---|---|
| PQRS inexistente | `EntityNotFoundException` | 404 |
| Admin intenta actuar sobre un PQRS que no le fue asignado | `PqrsAccessDeniedException` | 403 |
| Transición de estado inválida (ej. responder un PQRS ya `RESUELTA`) | `ValidationException` | 400 |
| Resolver un PQRS vinculado a una compra sin indicar `action` | `ValidationException` | 400 |
| Reportar un ítem que no es del comprador, ya fue reembolsado/cancelado, o está fuera de la ventana de reporte post-reclamo | `ObjectNotFoundException` / `InvalidStatusException` | 400 |
| No hay admins activos al crear | — (no es error) | PQRS queda `PENDIENTE_ASIGNACION`, se reintenta por scheduler |

Todas las excepciones están registradas en `GlobalExceptionHandler`, siguiendo el mismo patrón usado por el resto de módulos de la plataforma (branding, rifas, marketplace, etc.).
