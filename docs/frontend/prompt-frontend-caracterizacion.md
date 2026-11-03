# Tarea: ficha tributaria, compromiso de formalización y requerimientos del empresario

El backend (VerYGana API) ya implementa tres funciones nuevas sobre la información del empresario (rol `COMMERCIAL`) y su revisión por el oficial de cumplimiento (rol `COMPLIANCE_OFFICER`). Hay que construir las pantallas del frontend que las usan. No cambies reglas de negocio en el front: el backend valida todo y responde **400 con un mensaje en español** cuando algo no procede. Muestra ese mensaje tal cual al usuario.

Convenciones que ya usa la API y que se mantienen:
- Autenticación con el JWT de siempre. Los ids de usuario que salen al front son siempre `publicId` (UUID).
- Fechas `LocalDate` en formato `YYYY-MM-DD`; fechas con hora en ISO-8601.
- Montos en **centavos** (`amountCents`): muéstralos en pesos (÷100).
- Las listas paginadas usan el `PagedResponse` de siempre (`data` + `meta`) y aceptan `page`, `size` y `sort`.

---

## Parte 1 — Empresario (rol COMMERCIAL)

### 1.1 Paso nuevo del onboarding: ficha tributaria

El enum `OnboardingStep` tiene un valor nuevo, **`TAX_PROFILE_PENDING`**, entre `DOCUMENTS_PENDING` y `CONTRACT_PENDING`.

- Después de `POST /commercials/onboarding/documents/continue`, el paso queda en `TAX_PROFILE_PENDING`. Si el empresario ya había enviado la ficha en un ciclo anterior, queda directo en `CONTRACT_PENDING` (vuelve a leer el estado y navega según `currentStep`, como hoy).
- En `TAX_PROFILE_PENDING` muestra el formulario de la ficha tributaria. Al enviarlo con éxito el paso pasa a `CONTRACT_PENDING`: refresca el estado del onboarding y avanza.

**Formulario de la ficha.** Envíalo con `PUT /commercials/tax-profile`. Cada envío crea una versión nueva; el empresario puede volver a editar la ficha después del onboarding desde su perfil. El cuerpo tiene esta forma, agrupada por los incisos a–h del contrato:

```json
{
  "rutDocumentId": 123,                       // a) opcional: id del documento RUT ya cargado
  "rutResponsibilityCodes": ["05", "48"],     // a) opcional: códigos del catálogo RUT_RESPONSIBILITY
  "regimeCode": "ORD",                        // b) opcional: catálogo REGIME
  "activities": [                             // b) obligatorio, al menos una
    { "ciiuCode": "4711", "description": "Comercio al por menor", "main": true }
  ],
  "vatConditionCode": "RESP",                 // c) opcional: catálogo VAT_CONDITION
  "incConditionCode": null,                   // c) opcional: catálogo INC_CONDITION
  "taxConditionScope": "texto libre",         // c) opcional: a qué productos o servicios aplica
  "invoicingObligated": true,                 // d) obligatorio
  "invoicingMethodCode": "ELECTRONICA",       // d) opcional: catálogo INVOICING_METHOD
  "invoicingNotes": "texto",                  // d) opcional
  "withholdingAgent": false,                  // d) obligatorio
  "withholdingNotes": "texto",                // d) opcional
  "incomeReports": [                          // e) obligatorio, al menos uno
    {
      "concept": "VENTAS",                    // INGRESOS | VENTAS | RECAUDOS
      "periodStart": "2026-01-01",
      "periodEnd": "2026-06-30",              // no puede ser una fecha futura
      "amountCents": 1500000000,
      "currency": "COP",
      "scope": "EXTERNAL_ONLY",               // EXTERNAL_ONLY | PLATFORM_ONLY | CONSOLIDATED_INCLUDES_PLATFORM
      "supportDocumentId": null,              // opcional: id de un documento del expediente
      "notes": "texto"
    }
  ],
  "currentCapacity": "texto",                 // g) obligatorio: lo que hoy puede atender
  "growthScenario": "texto",                  // g) opcional
  "growthMeasures": "texto",                  // g) opcional
  "hasAccountingAdvisor": true,               // h) obligatorio
  "advisorScope": "texto"                     // h) opcional (se ignora si hasAccountingAdvisor = false)
}
```

Reglas que el formulario debe dejar claras al usuario. El backend las valida igual:
- Exactamente **una** actividad marcada como principal, sin CIIU repetidos. El CIIU tiene 4 dígitos.
- **Ingresos (inciso e):** el empresario debe incluir operaciones de fuera de VERYGANA. Explica bien el campo `scope`:
  - "Solo fuera de VERYGANA", "Solo en VERYGANA" o "Total que ya incluye VERYGANA".
  - Para el mismo concepto y un periodo superpuesto, un total consolidado no puede convivir con otro valor. El backend lo rechaza para no contar dos veces las ventas de la plataforma.
- Solo periodos ya cerrados: nada de estimaciones futuras.
- Los selectores de códigos salen del catálogo (abajo) y son **opcionales**. Si el catálogo de un tipo viene vacío, muestra el campo deshabilitado con un texto tipo "Aún no disponible".
- Mencionar un asesor contable **no** le da acceso a nadie. Si quieres, deja una nota informativa.

**Endpoints de la ficha:**

| Método | Ruta | Uso |
|---|---|---|
| GET | `/commercials/tax-profile` | Ficha vigente. **204 sin cuerpo** si todavía no la ha enviado. |
| PUT | `/commercials/tax-profile` | Enviar la ficha (crea una versión nueva). |
| GET | `/commercials/tax-profile/versions` | `[{ versionNumber, submittedAt }]` |
| GET | `/commercials/tax-profile/versions/{versionNumber}` | Detalle de una versión anterior (solo lectura). |
| GET | `/commercials/tax-profile/catalog` | Códigos vigentes: `[{ id, catalogType, code, label, legalBasis, validFrom, validTo, active }]`. Agrúpalos por `catalogType`: `REGIME`, `RUT_RESPONSIBILITY`, `VAT_CONDITION`, `INC_CONDITION`, `INVOICING_METHOD`. |

Respuesta de `GET`/`PUT /commercials/tax-profile`:

```json
{
  "status": "SUBMITTED",                     // SUBMITTED | OBSERVED | REVIEWED
  "currentVersion": 2,
  "updatedAt": "...",
  "version": { /* los mismos campos del envío + versionNumber, submittedAt, establishmentsCount */ },
  "sectionReviews": [                        // revisión de compliance por sección; si falta una, no se ha revisado
    { "section": "A_RUT", "result": "OBSERVED", "notes": "El RUT es de otro titular", "reviewedAt": "..." }
  ],
  "activeEstablishments": [ /* ver 1.2 */ ],
  "formalizationCommitment": {                // puede ser null; ver 1.3
    "id": 5, "status": "MANIFESTED", "declarationVersion": "1", "contractId": 30,
    "contractVersion": 2, "occurredAt": "...", "detail": null, "actorLegalRepName": "Ana Pérez"
  }
}
```

Las secciones son `A_RUT`, `B_REGIME_ACTIVITIES`, `C_VAT_INC`, `D_INVOICING_WITHHOLDING`, `E_INCOME`, `F_ESTABLISHMENTS`, `G_GROWTH_CAPACITY` y `H_ADVISORY`; los resultados son `VERIFIED`, `OBSERVED` y `NOT_APPLICABLE`. Muestra al lado de cada bloque del formulario si está "Pendiente de revisión", "Verificada", "No aplica" u "Observada" (con la nota). Si `status = OBSERVED`, destaca que hay observaciones por resolver: se resuelven enviando una versión corregida.

### 1.2 Establecimientos (inciso f)

| Método | Ruta | Cuerpo |
|---|---|---|
| GET | `/commercials/establishments` | — devuelve activos y cerrados |
| POST | `/commercials/establishments` | `{ name, address, municipalityCode, relation }` |
| POST | `/commercials/establishments/{establishmentId}/close` | `{ reason }` (obligatorio) |

- Un establecimiento tiene `{ id, name, address, municipalityCode, municipalityName, relation, status, createdAt, closedAt, closedReason }`.
- `relation` puede ser `OWNED`, `LEASED`, `FRANCHISE` u `OTHER`; `status` puede ser `ACTIVE` o `CLOSED`.
- `municipalityCode` es el código DANE de 5 dígitos: reutiliza el selector departamento → municipio que ya existe.
- **No hay editar ni borrar.** Para corregir, se cierra el establecimiento con un motivo y se crea otro; el historial se conserva. Muestra los cerrados aparte.
- El backend rechaza URLs, correos o redes sociales como dirección ("un canal digital no es un establecimiento") y la misma dirección repetida en el mismo municipio.
- Ponlo dentro de la ficha (inciso f) y también en el perfil del empresario. Puede registrarlos antes de enviar la ficha: el número que guarda la ficha se calcula con los activos al momento del envío.

### 1.3 Compromiso de formalización en la aprobación del contrato (Hoja Final, apartado 9)

Es lo más delicado legalmente. Síguelo al pie de la letra.

**Dónde va:** solo en la pantalla donde el empresario aprueba el Contrato Marco (paso `BUSINESS_REVIEW_PENDING`, botón que llama `POST /commercials/onboarding/contract/approve`). **En ningún otro lugar se pide aceptar esta declaración.**

1. Al abrir esa pantalla, llama `GET /commercials/declarations/FORMALIZATION_COMMITMENT?context=CONTRACT_SHEET`. Responde:
   ```json
   { "code": "FORMALIZATION_COMMITMENT", "version": "1", "fullText": "Reconozco que mi participación...",
     "textHash": "...", "manifestation": null }
   ```
2. Muestra un bloque titulado "Hoja Final de Aceptación Electrónica — Apartado 9. Compromiso de crecimiento y formalización" con el **`fullText` completo y literal**. No lo resumas ni lo recortes dentro de la etiqueta de la casilla.
3. Debajo, una **casilla NO premarcada**: "Manifiesto expresamente este compromiso". Reglas:
   - Empieza siempre desmarcada.
   - No se marca sola por marcar otras casillas, por navegar, por pagar ni por enviar el formulario.
   - Aprobar el contrato **no** exige marcarla: el botón "Aprobar contrato" funciona con la casilla marcada o sin marcar.
4. Ofrece además una opción explícita y separada para negarse, por ejemplo un enlace "No acepto este compromiso". Al usarla, pide un motivo opcional en un modal.
5. Al aprobar, envía:
   ```http
   POST /commercials/onboarding/contract/approve
   Idempotency-Key: <UUID generado una vez por intento de aprobación; reutilízalo si reintentas>
   Content-Type: application/json

   { "declarationVersion": "1", "decision": "ACCEPT" }
   ```
   - Casilla marcada: `"decision": "ACCEPT"`.
   - Negativa expresa: `"decision": "REFUSE"`, con `"refusalReason": "texto opcional"`.
   - Casilla sin marcar: no envíes `decision` (o envía `null`).
   - `declarationVersion` es siempre la `version` que mostraste.
   - El cuerpo es opcional para el backend, pero envíalo siempre desde esta pantalla.
6. Si `manifestation` ya viene con `status = "MANIFESTED"` (por ejemplo, el contrato se regeneró), **no muestres la casilla**. Muestra la evidencia existente: "Manifestado el {occurredAt} por {actorLegalRepName} (versión {declarationVersion})". Aprueba sin enviar `decision`.
7. Ninguna opción (aceptar, negarse o dejarla sin marcar) bloquea la aprobación. Si no se marca o se niega, el empresario recibirá luego una solicitud de aclaración (ver 1.5). Puedes avisarlo con un texto informativo, sin tono de sanción.

**En la ficha tributaria** (y en el perfil), muestra `formalizationCommitment` **solo como referencia de lectura**:
- "Compromiso de formalización: manifestado el …" o "Pendiente de aclaración".
- Un enlace para leer el texto: `GET /commercials/declarations/FORMALIZATION_COMMITMENT?context=TAX_PROFILE`.
- **Nunca una casilla.**

Estados posibles de `manifestation.status`: `MANIFESTED`, `NOT_MANIFESTED`, `EXPRESS_REFUSAL`, `TECHNICAL_INCIDENT`.

### 1.4 Expediente documental (después del onboarding)

Pantalla nueva "Mi expediente" en el perfil del empresario, visible cuando el onboarding está `COMPLETED`.

| Método | Ruta | Uso |
|---|---|---|
| GET | `/commercials/documents` | Vigentes (`status = VALIDATED`) e historial (`status = SUPERSEDED`). |
| POST | `/commercials/documents/prepare-upload` | `{ documentType, originalFileName, contentType, sizeBytes, replacesDocumentId? }` → `{ documentId, permission }` |
| POST | `/commercials/documents/{documentId}/confirm` | Confirma la carga y devuelve el documento. |

- Usa **el mismo flujo de carga del onboarding**: `prepare-upload` → subir el archivo a la URL prefirmada de `permission` → `confirm`. Solo PDF, JPEG o PNG.
- **Reemplazar** = cargar con `replacesDocumentId` igual al id del vigente, del mismo tipo. El anterior pasa al historial; nunca se borra.
- Cada documento trae `{ id, documentType, originalFileName, sizeBytes, status, uploadedAt, replacesDocumentId, supersededAt, reviewStatus, reviewedAt, reviewNotes, holderIdentification, issuedAt, expiresAt, expired, downloadUrl }`.
  - `reviewStatus` puede ser `NOT_REVIEWED`, `ACCEPTED`, `OBSERVED` o `REJECTED`. Muéstralo como insignia; si hay `reviewNotes`, muéstralas.
  - Si `expired = true`, insignia "Vencido" y botón "Reemplazar".
  - Si `expiresAt` es null, no muestres vencimiento.
  - `downloadUrl` dura 5 minutos: pídela de nuevo al recargar la lista.
- Agrupa por tipo: el vigente arriba y el historial plegado debajo.

### 1.5 Requerimientos de información (solicitudes de VERYGANA)

Pantalla "Solicitudes de información", con una insignia de pendientes en el menú. Además llega una notificación interna por el canal de notificaciones que ya existe.

| Método | Ruta | Uso |
|---|---|---|
| GET | `/commercials/information-requests` | Lista (más recientes primero). |
| GET | `/commercials/information-requests/{requestPublicId}` | Detalle. |
| POST | `/commercials/information-requests/{requestPublicId}/responses` | `{ message?, documentIds?: [ids] }` |

Cada requerimiento trae:

```json
{
  "publicId": "...", "trigger": "DOCUMENT_EXPIRED", "reason": "El documento PERMISO_SECTORIAL venció el 01/10/2026.",
  "relationToOperation": "Numeral 4.8: ...", "status": "OPEN", "dueDate": null, "issuedBySystem": true,
  "issuedAt": "...", "closedAt": null, "closingNotes": null,
  "items": [{ "itemType": "DOCUMENT", "documentType": "PERMISO_SECTORIAL", "fieldPath": null, "establishmentId": null,
              "description": "Cargue una versión vigente...", "acceptableResponse": "...", "linkedDocumentId": null }],
  "responses": [{ "message": "...", "documentIds": [12], "receivedAt": "..." }],
  "reviews": [{ "result": "INSUFFICIENT", "notes": "El permiso es de otra sede", "reviewedAt": "..." }]
}
```

**Estados** (muéstralos con texto claro):

| `status` | Texto sugerido |
|---|---|
| `OPEN` | Pendiente de tu respuesta |
| `RESPONDED` | En revisión por VERYGANA |
| `OBSERVED` | Tiene observaciones: responde de nuevo (muestra la última nota de `reviews`) |
| `CLOSED_SATISFIED` | Resuelto |
| `CLOSED_WITH_FINDING` | Cerrado por VERYGANA |
| `CANCELLED` | Anulado |

- Solo se puede responder en `OPEN` u `OBSERVED`.
- Muestra `reason`, `relationToOperation` (el "por qué se pide") y cada ítem con su `description` y `acceptableResponse`.
- Si un ítem trae `linkedDocumentId`, indica "Ya tenemos este documento en tu expediente; VERYGANA revisará si basta".
- **Responder:** un campo de texto más la opción de adjuntar documentos. Para adjuntar, primero se suben al expediente (1.4, normalmente como reemplazo) y luego se envían sus ids en `documentIds`. Hace falta texto o al menos un documento.
- Tras responder, el estado pasa a `RESPONDED`: deja claro que **responder no cierra la solicitud**, la cierra la revisión.
- Si `trigger` es `MISSING_MANIFESTATION`, `EXPRESS_REFUSAL` o `INCIDENT`, la solicitud trata del compromiso de formalización. Usa un tono de aclaración, no de sanción.

---

## Parte 2 — Panel de cumplimiento (rol COMPLIANCE_OFFICER)

Agrega estas secciones al panel de compliance que ya existe.

### 2.1 Fichas tributarias

| Método | Ruta | Uso |
|---|---|---|
| GET | `/compliance/tax-profiles?status=&page=&size=` | Cola de fichas. Filtro `status`: `SUBMITTED`, `OBSERVED` o `REVIEWED`. Filas: `{ commercialPublicId, companyName, status, currentVersion, updatedAt }`. |
| GET | `/compliance/tax-profiles/{commercialPublicId}` | Detalle: `{ commercialPublicId, companyName, nit, commercialActivityType, profile, versions, establishmentHistory }`. `profile` tiene la misma forma que la vista del empresario. |
| GET | `/compliance/tax-profiles/{commercialPublicId}/versions/{n}` | Versión anterior, para comparar. |
| POST | `/compliance/tax-profiles/{commercialPublicId}/sections/{section}/review` | `{ result, notes }`. `notes` es obligatorio si `result = OBSERVED`. |

- La pantalla de detalle muestra los 8 incisos (a–h); cada uno con su último resultado y botones "Verificar", "Observar" y "No aplica".
- Muestra `commercialActivityType` (la vocación) **solo para contrastar**, con un texto del tipo "La ficha no modifica la vocación".
- Ofrece comparar la versión actual con una anterior.

### 2.2 Documentos del expediente

| Método | Ruta | Uso |
|---|---|---|
| GET | `/compliance/commercials/{commercialPublicId}/documents` | Vigentes e historial. |
| POST | `/compliance/commercial-documents/{documentId}/review` | `{ result, holderIdentification?, issuedAt?, expiresAt?, notes? }` |

- `result` puede ser `ACCEPTED`, `OBSERVED` o `REJECTED`.
- Para `ACCEPTED` en un RUT, Cámara de Comercio, certificación bancaria o cédula del representante, `holderIdentification` (NIT o cédula como figura en el documento) es obligatorio. El backend rechaza si no coincide con el empresario.
- `notes` es obligatorio para `OBSERVED` o `REJECTED`.
- `expiresAt` solo se llena si el documento tiene una vigencia comprobable; si no, se deja vacío y el documento nunca vence.
- Solo se revisan documentos con `status = VALIDATED`.

### 2.3 Catálogo tributario

| Método | Ruta | Uso |
|---|---|---|
| GET | `/compliance/tax-catalog` | Todos, incluidos los retirados. |
| POST | `/compliance/tax-catalog` | `{ catalogType, code, label, legalBasis, validFrom }` |
| POST | `/compliance/tax-catalog/{entryId}/retire` | `{ validTo }` |

Un código no se edita ni se borra: se retira con una fecha de fin. Pide siempre el fundamento (`legalBasis`).

### 2.4 Compromiso de formalización (historial)

`GET /compliance/commercials/{commercialPublicId}/declarations/FORMALIZATION_COMMITMENT` devuelve todas las manifestaciones del empresario (`MANIFESTED`, `NOT_MANIFESTED`, `EXPRESS_REFUSAL` o `TECHNICAL_INCIDENT`), con contrato, versión, fecha y actor. Ponlo en la ficha del empresario en compliance. Solo lectura.

### 2.5 Requerimientos

| Método | Ruta | Uso |
|---|---|---|
| GET | `/compliance/information-requests?status=&commercialPublicId=&page=&size=` | Cola. |
| GET | `/compliance/information-requests/{requestPublicId}` | Detalle; incluye `communications` (`IN_APP`, `SENT`/`FAILED`). |
| POST | `/compliance/information-requests` | Crear (ver abajo). |
| POST | `/compliance/information-requests/{requestPublicId}/review` | `{ result: "SUFFICIENT" \| "INSUFFICIENT", notes }`. `notes` es obligatorio si `INSUFFICIENT`. Solo en estado `RESPONDED`. |
| POST | `/compliance/information-requests/{requestPublicId}/cancel` | `{ reason }` |

Crear:

```json
{
  "commercialPublicId": "...",
  "trigger": "NEW_REQUIREMENT",
  "reason": "...",
  "relationToOperation": "...",
  "dueDate": null,
  "alertId": null,
  "items": [
    { "itemType": "DOCUMENT", "documentType": "PERMISO_SECTORIAL", "establishmentId": 3,
      "description": "...", "acceptableResponse": "..." }
  ]
}
```

- `trigger` puede ser `DOCUMENT_EXPIRED`, `ACTIVITY_CHANGE`, `ESTABLISHMENT_CHANGE`, `MATERIAL_OPERATION_INCREASE`, `NEW_REQUIREMENT`, `INCONSISTENCY`, `INCIDENT`, `RISK`, `MISSING_MANIFESTATION` o `EXPRESS_REFUSAL`.
- `itemType` puede ser `FIELD`, `DOCUMENT` o `CLARIFICATION`:
  - `DOCUMENT` exige `documentType`.
  - `FIELD` exige `fieldPath`.
- `items` no puede ir vacío: el formulario debe obligar a decir qué se pide concretamente, nunca "actualice toda su información".
- `dueDate` solo se llena si una regla aplicable fija un plazo.
- Si se crea desde una alerta, envía su `alertId`: la alerta queda revisada y vinculada.

### 2.6 Hallazgos y medidas

| Método | Ruta | Uso |
|---|---|---|
| POST | `/compliance/findings` | Registrar un hallazgo. |
| GET | `/compliance/commercials/{commercialPublicId}/findings` | Historial. |

```json
{
  "commercialPublicId": "...", "requestPublicId": null,
  "factObserved": "...", "evidence": "...",
  "classification": "VERIFIED_FALSE_INFO",
  "severity": "HIGH",
  "impact": "...", "remediable": false,
  "contractualBasis": "...",
  "measure": "SUSPENSION",
  "measureScope": "..."
}
```

- `classification` puede ser `SUBSANABLE_ERROR`, `LACK_OF_UPDATE`, `ISOLATED_INCONSISTENCY`, `VERIFIED_FALSE_INFO` o `EXPRESS_REFUSAL`.
- `severity` puede ser `LOW`, `MEDIUM` o `HIGH`.
- `measure` puede ser `NONE`, `WARNING`, `PREVENTIVE_RESTRICTION`, `SUSPENSION` o `TERMINATION_REFERRAL`.

**Importante para la UI:**
- `PREVENTIVE_RESTRICTION` y `SUSPENSION` **cambian el estado de la cuenta del empresario de inmediato**. Antes de enviar, pide una confirmación explícita que lo diga.
- `TERMINATION_REFERRAL` no cambia la cuenta: solo remite al procedimiento.
- `contractualBasis`, `impact` y `remediable` son obligatorios: la decisión tiene que quedar justificada.
- Con `SUBSANABLE_ERROR` o `ISOLATED_INCONSISTENCY` solo se permite `NONE` o `WARNING`. Deshabilita las demás medidas en el selector; el backend las rechaza igual.
- `EXPRESS_REFUSAL` solo procede si el empresario tiene una negativa expresa registrada (ver 2.4).
- Si se envía `requestPublicId`, ese requerimiento queda cerrado con el hallazgo.

### 2.7 Incidencias técnicas

- `POST /compliance/incidents` con `{ commercialPublicId, context, description, requestPublicId? }`.
- `GET /compliance/commercials/{commercialPublicId}/incidents` devuelve el historial.

Sirven para dejar constancia de fallas (por ejemplo, una respuesta que no llegó) y que no se confundan con una negativa del empresario. Muéstralas en la ficha del empresario.

### 2.8 Alertas y reglas

| Método | Ruta | Uso |
|---|---|---|
| GET | `/compliance/alerts?pendingOnly=true&page=&size=` | Cola de alertas. |
| POST | `/compliance/alerts/{alertId}/review` | `{ notes }` — marcar como revisada. |
| GET | `/compliance/alert-rules` | Reglas con todas sus versiones. |
| POST | `/compliance/alert-rules` | `{ code, ruleType: "PLATFORM_SALES_GROWTH", description, legalBasis, windowDays, minGrowthPct, minCurrentSalesCents }` |
| POST | `/compliance/alert-rules/{ruleId}/deactivate` | — |

- **Alertas:** cada alerta trae `{ id, ruleCode, ruleVersion, trigger, commercialPublicId, companyName, detectedAt, dataUsedJson, message, reviewedAt, reviewNotes, requestPublicId }`. Las que vienen de cambios del empresario (`ACTIVITY_CHANGE`, `ESTABLISHMENT_CHANGE`) llegan sin regla.
- **Acciones sobre una alerta:** "Marcar revisada" (con nota) o "Abrir requerimiento", que lleva al formulario de 2.5 con `alertId` y el empresario ya cargados.
- **Mensaje:** muestra `message` completo, porque incluye el alcance ("solo incluye operaciones en VERYGANA…"). Muestra `dataUsedJson` como tabla.
- **Reglas:** crear una regla con un `code` existente crea una versión nueva y desactiva la anterior. Muestra el historial de versiones. No pongas valores por defecto en el formulario: los umbrales los decide compliance, con fundamento.

---

## Criterios de aceptación

- La casilla del compromiso aparece **solo** en la aprobación del contrato, siempre desmarcada, con el texto completo, y no bloquea la aprobación. En cualquier otra pantalla el compromiso es solo lectura.
- El empresario puede completar el onboarding pasando por `TAX_PROFILE_PENDING`.
- Ninguna pantalla permite borrar documentos del expediente ni establecimientos: solo reemplazar o cerrar, y el historial queda visible.
- Los errores 400 del backend se muestran con su mensaje.
- Las medidas que cambian el estado de la cuenta piden confirmación explícita.
- Las pantallas del empresario solo usan rutas `/commercials/...`; las de compliance, `/compliance/...`.
