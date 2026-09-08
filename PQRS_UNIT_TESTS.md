# Pruebas unitarias — Dominio PQRS

Inventario completo de las pruebas unitarias del dominio `PQRS` (JUnit5 + Mockito/AssertJ, sin Spring context). PQRS era el dominio más maduro de los cuatro auditados: solo `PqrsAssetMapper` no tenía ningún test — el resto (services, `PqrsMapper`, utils, modelo, controllers unitarios) ya estaba cubierto. Resultado confirmado corriendo `mvn test` el 2026-08-27 junto con el resto del lote de PQRS: **45 pruebas nuevas (mapper + integración + repositorio), 0 fallos, 0 errores, BUILD SUCCESS**.

**Nuevo** = creada en esta sesión. **Existente** = ya estaba en el repo.

---

## Controllers (Mockito puro) — Existentes

### `PqrsControllerTest` — cubre `PqrsController`

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `createPqrs_returns201WithBody` | Extrae el `userId` del JWT, delega en el service, responde 201 | ✅ Pasa |
| `getMyPqrs_returns200WithPagedBody` | Delega con `userId` y pageable, responde 200 | ✅ Pasa |
| `getMyPqrsDetail_returns200WithBody` | Delega con el id del path y `userId`, responde 200 | ✅ Pasa |
| `prepareAssetUpload_returns200WithBody` | Delega en `PqrsAssetService` con el `userId` | ✅ Pasa |
| `confirmAssetUpload_returns200WithBody` | Delega con el id del path y `userId` | ✅ Pasa |
| `owner_delegatesWithIsAdminFalse` | `streamAsset`: consumer dueño delega con `isAdmin=false` | ✅ Pasa |
| `admin_delegatesWithIsAdminTrue` | `streamAsset`: admin delega con `isAdmin=true` | ✅ Pasa |

### `PqrsAdminControllerTest` — cubre `PqrsAdminController`

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getAssignedPqrs_delegatesWithFiltersAndReturns200` | Pasa el `adminId` del JWT y los filtros status/type/pageable | ✅ Pasa |
| `getPqrsDetail_returns200WithBody` | Delega con el id del path y `adminId` | ✅ Pasa |
| `markUnderReview_delegatesAndReturns200` | Delega y responde 200 sin body | ✅ Pasa |
| `respondToPqrs_delegatesAndReturns200` | Delega con el body de la respuesta, 200 sin body | ✅ Pasa |

---

## Servicios — Existentes

### `PqrsServiceImplTest` — cubre `PqrsServiceImpl`

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `withAvailableAdmin_assignsAdminAndNotifies` | `createPqrs` con admin disponible: asigna, notifica, responde DTO | ✅ Pasa |
| `withNoAvailableAdmin_savesUnassignedWithoutNotifyingAdmin` | Sin admin disponible: guarda sin asignar, no notifica | ✅ Pasa |
| `requesterNotFound_throwsEntityNotFoundException` | Solicitante inexistente: no persiste nada | ✅ Pasa |
| `createsReclamoLinkedToItemWithReason` | `createPqrsForPurchaseItem`: crea RECLAMO vinculado al ítem con el motivo | ✅ Pasa |
| `withAssetIds_claimsAssetsAndReturnsThemInResponse` | Con evidencia: reclama los assets y los devuelve en el DTO | ✅ Pasa |
| `invalidAssets_propagatesExceptionWithoutNotifying` | Evidencia inválida: propaga sin notificar | ✅ Pasa |
| `returnsPagedResponseMappedFromRepository` | `getMyPqrs`: filtra por `requesterId`, mapea a DTO | ✅ Pasa |
| `ownPqrs_returnsMappedDetail` | `getMyPqrsDetail`: PQRS propio retorna el detalle | ✅ Pasa |
| `notFound_throwsEntityNotFoundException` | PQRS inexistente | ✅ Pasa |
| `belongsToAnotherUser_throwsEntityNotFoundException` | PQRS de otro usuario: oculta la existencia (no es un 403) | ✅ Pasa |
| `delegatesToRepositoryWithFilters` | `getAssignedPqrs`: filtros status/type, DTO de admin | ✅ Pasa |
| `assignedToThisAdmin_returnsMappedDetail` | `getPqrsDetailForAdmin`: asignado a este admin retorna el detalle | ✅ Pasa |
| `assignedToAnotherAdmin_throwsAccessDenied` | Asignado a otro admin: `PqrsAccessDeniedException` | ✅ Pasa |
| `unassigned_throwsAccessDenied` | Sin admin asignado todavía: `PqrsAccessDeniedException` | ✅ Pasa |
| `receivedStatus_movesToEnRevision` | `markUnderReview`: RECIBIDA pasa a EN_REVISION | ✅ Pasa |
| `wrongStatus_throwsValidationException` (markUnderReview) | Ya no está en RECIBIDA: no persiste | ✅ Pasa |
| `notOwnedByAdmin_throwsAccessDenied` (markUnderReview) | Admin no dueño del PQRS | ✅ Pasa |
| `resolvablePqrs_marksResolvedAndNotifiesRequester` | `respondToPqrs`: guarda respuesta, marca RESUELTA, notifica | ✅ Pasa |
| `alreadyResolved_throwsValidationException` | Ya resuelta: no notifica | ✅ Pasa |
| `notOwnedByAdmin_throwsAccessDenied` (respondToPqrs) | Admin no dueño | ✅ Pasa |
| `marketplaceLinkedWithoutAction_throwsValidationException` | Vinculado a un ítem sin `action`: no ejecuta reembolso | ✅ Pasa |
| `marketplaceLinkedWithDismiss_resolvesWithoutRefund` | `action=DISMISS`: restaura status previo, resuelve sin reembolso | ✅ Pasa |
| `marketplaceLinkedWithRefund_triggersRefund` | `action=REFUND`: dispara el reembolso antes de resolver | ✅ Pasa |
| `marketplaceLinkedWithRefundAndCashPortion_staysOpenUntilPaid` | `REFUND` con porción en efectivo pendiente: PENDIENTE_PAGO_REEMBOLSO, no notifica todavía | ✅ Pasa |
| `noPending_doesNothing` | `retryPendingAssignments`: sin pendientes, no hace nada | ✅ Pasa |
| `adminAvailable_assignsAndNotifies` | Con admin disponible: asigna, RECIBIDA, notifica | ✅ Pasa |
| `noAdminAvailable_leavesPqrsUnchanged` | Sin admin disponible todavía: sin cambios | ✅ Pasa |
| `atRiskWithAssignedAdmin_sendsEmail` | `sendSlaAlerts`: en riesgo con admin asignado envía alerta | ✅ Pasa |
| `atRiskWithoutAssignedAdmin_skipsSilently` | En riesgo sin admin: no falla, no envía correo | ✅ Pasa |
| `noneAtRisk_sendsNothing` | Sin PQRS en riesgo: no envía nada | ✅ Pasa |

### `PqrsAssignmentServiceTest` — cubre `PqrsAssignmentService`

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `noActiveAdmins_returnsEmpty` | Sin admins activos: `Optional.empty()`, no guarda nada | ✅ Pasa |
| `withCandidate_updatesLastAssignedAtAndReturnsIt` | Con candidato: actualiza `lastPqrsAssignedAt` y lo devuelve | ✅ Pasa |
| `requestsOnlyOneCandidateAtATime` | Pide solo 1 candidato (`PageRequest.of(0,1)`), no lista completa | ✅ Pasa |

### `PqrsAssetServiceImplTest` — cubre `PqrsAssetServiceImpl`

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `validFile_persistsPendingAndReturnsUploadUrl` | `prepareUpload`: archivo válido persiste PENDING, devuelve URL prefirmada | ✅ Pasa |
| `oversizedFile_throwsValidationException` | Archivo demasiado grande: no persiste | ✅ Pasa |
| `unsupportedMimeType_throwsValidationException` | Tipo no soportado | ✅ Pasa |
| `validUpload_marksValidatedAndDelegatesMapping` | `confirmUpload`: pasa a VALIDATED, delega el mapeo | ✅ Pasa |
| `wrongOwner_throwsAccessDenied` | Dueño incorrecto | ✅ Pasa |
| `alreadyValidated_throwsValidationException` | Ya confirmado | ✅ Pasa |
| `nullList_isNoOp` | `validateAndClaimAssets`: lista null, no toca el repo | ✅ Pasa |
| `emptyList_isNoOp` | Lista vacía, no toca el repo | ✅ Pasa |
| `validOwnedConfirmedAssets_claimsAndPersists` | Assets válidos/propios/confirmados: los reclama | ✅ Pasa |
| `assetOwnedByAnotherUser_throwsAccessDeniedAndClaimsNothing` | De otro usuario: no reclama ninguno | ✅ Pasa |
| `assetStillPending_throwsValidationException` | Todavía PENDING (no confirmado) | ✅ Pasa |
| `assetAlreadyClaimedByAnotherPqrs_throwsValidationException` | Ya reclamado por otro Pqrs | ✅ Pasa |
| `exceedsMaxCount_throwsValidationException` | Supera el máximo permitido, sin consultar el repo | ✅ Pasa |
| `unknownId_throwsValidationException` | Id inexistente | ✅ Pasa |
| `notOwnerNotAdmin_throwsAccessDeniedWithoutTouchingR2` | `streamAsset`: no es dueño ni admin, no toca R2 | ✅ Pasa |
| `unknownAsset_throwsNotFound` | Id inexistente | ✅ Pasa |

---

## Mappers

### `PqrsMapperTest` — cubre `PqrsMapper` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `toResponseDTO_mapsAllFlatFields` | Copia todos los campos planos, incl. el radicado derivado (`getBased`) | ✅ Pasa |
| `toResponseDTO_nullInput_returnsNull` | `null` → `null` | ✅ Pasa |
| `toAdminDetailDTO_mapsRequesterInfoAndResolvesName` | Copia datos del requester y resuelve su nombre | ✅ Pasa |
| `toAdminDetailDTO_withoutPurchaseItem_productAndCommercialAreNull` | PQRS genérico (sin `purchaseItem`): product/commercial quedan null, sin NPE | ✅ Pasa |
| `toAdminDetailDTO_withPurchaseItem_mapsProductAndCommercialContext` | PQRS de marketplace: mapea el contexto completo | ✅ Pasa |

### `PqrsAssetMapperTest` — cubre `PqrsAssetMapper` — **Nueva**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `toResponseDTO_buildsViewUrlFromAppBaseUrlAndAssetId` | Arma `viewUrl` como `appBaseUrl + /pqrs/assets/{id}/view` | ✅ Pasa |
| `toResponseDTO_nullInput_returnsNull` | `null` → `null` | ✅ Pasa |
| `toResponseDTO_mapsFlatFieldsDirectly` | Copia `id`, `originalFileName`, `mediaType`, `mimeType`, `sizeBytes`, `createdAt` | ✅ Pasa |

---

## Utils — Existentes

### `BusinessDayCalculatorTest`

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `zeroBusinessDays_returnsSameDate` | 0 días hábiles: misma fecha de inicio | ✅ Pasa |
| `withinSameWeek_addsConsecutiveDays` | Suma dentro de la misma semana: no salta días | ✅ Pasa |
| `crossingWeekend_skipsSaturdayAndSunday` | Cruzando el fin de semana: salta sábado y domingo | ✅ Pasa |
| `multipleWeeks_skipsAllWeekendsInBetween` | 15 días hábiles (SLA típico de PETICION) | ✅ Pasa |

### `RequesterNameResolverTest`

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `consumerDetails_returnsFullName` | ConsumerDetails: concatena nombre + apellido | ✅ Pasa |
| `commercialDetails_returnsCompanyName` | CommercialDetails: usa el nombre de la empresa | ✅ Pasa |
| `gameDesignerDetails_returnsFullName` | GameDesignerDetails: concatena nombre + apellido | ✅ Pasa |
| `unmappedRole_fallsBackToEmail` | Rol no contemplado (ej. AdminDetails): cae al email | ✅ Pasa |
| `nullUserDetails_fallsBackToEmail` | Sin UserDetails asociado: cae al email | ✅ Pasa |

---

## Modelo — Existente

### `PqrsTest` — cubre `Pqrs`

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `trueOnlyWhenReceived` | `canBeReviewed`: true solo en RECIBIDA | ✅ Pasa |
| `falseForOtherStatuses` (canBeReviewed) | False en cualquier otro status | ✅ Pasa |
| `trueForReceivedAndUnderReview` | `canBeResolved`: true para RECIBIDA y EN_REVISION | ✅ Pasa |
| `falseForOtherStatuses` (canBeResolved) | False para PENDIENTE_ASIGNACION, RESUELTA, CERRADA | ✅ Pasa |
| `withoutId_returnsNull` | `getBased` sin id: retorna null | ✅ Pasa |
| `withIdAndCreatedAt_formatsRadicado` | Con id y createdAt: `PQRS-<año>-<id 6 dígitos>` | ✅ Pasa |
| `withIdButNoCreatedAt_fallsBackToCurrentYear` | Sin createdAt: usa el año actual | ✅ Pasa |
| `withAssignedAdmin_defaultsToReceived` | `onCreate`: con admin ya asignado, status inicial RECIBIDA | ✅ Pasa |
| `withoutAssignedAdmin_defaultsToPendingAssignment` | Sin admin: status inicial PENDIENTE_ASIGNACION | ✅ Pasa |
| `withExplicitStatus_doesNotOverrideIt` | Status explícito: no se sobrescribe | ✅ Pasa |
| `onUpdate_refreshesUpdatedAt` | `onUpdate`: refresca `updatedAt` | ✅ Pasa |

---

## Resumen

- **Clases nuevas**: 1 (`PqrsAssetMapperTest`, 3 pruebas).
- **Clases existentes documentadas**: 9.
- **Total del dominio PQRS de esta ronda (mapper + integración + repositorio)**: 45 pruebas nuevas, 0 fallos, 0 errores — `mvn test`, 2026-08-27.
