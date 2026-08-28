# Pruebas unitarias — Dominio Raffles

Inventario completo de pruebas unitarias (JUnit 5 + Mockito/AssertJ, sin Spring context, sin base de datos) del dominio `raffles`. Cada clase de test se agrupa por el componente de producción que cubre. Resultado confirmado corriendo `mvn test` el 2026-08-26 sobre las 45 clases del dominio (unitarias + integración + repositorio): **309 pruebas, 0 fallos, 0 errores, BUILD SUCCESS**.

**Nuevo** = creada en esta sesión de trabajo. **Existente** = ya estaba en el repo.

---

## Controllers (Mockito puro, sin `@WebMvcTest`)

### `RaffleControllerTest` — cubre `RaffleController` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getSummaryRafflesByFilters_delegates` | Delega con status/search/drawDate/type/pageable al service | ✅ Pasa |
| `getRaffleById_admin_delegatesWithIsAdminTrue` | Admin: delega en el service con `isAdmin=true` | ✅ Pasa |
| `getRaffleById_nonAdmin_delegatesWithIsAdminFalse` | Consumer/anónimo: delega con `isAdmin=false` | ✅ Pasa |
| `getDrawStatus_combinesViewerCountAndRaffleData` | Combina el conteo de espectadores de `WaitingRoomService` con los datos de la rifa | ✅ Pasa |
| `getMyRafflesByStatus_delegates` | Extrae el `consumerId` del JWT | ✅ Pasa |
| `countMyRafflesByStatus_delegates` | Extrae el `consumerId` del JWT | ✅ Pasa |

### `UserRaffleTicketControllerTest` — cubre `UserRaffleTicketController` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getUserTicketsByRaffle_delegates` | Delega con `consumerId` del JWT, `raffleId` del path y el pageable | ✅ Pasa |
| `getWinnerUserTotalTickets_delegates` | Delega con el `consumerId` del JWT | ✅ Pasa |

### `RaffleDrawWebSocketControllerTest` — cubre `RaffleDrawWebSocketController` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `joinWaitingRoom_addsViewer` | Agrega al `WaitingRoomService` al viewer con el `sessionId` del header STOMP | ✅ Pasa |
| `leaveWaitingRoom_removesViewer` | Remueve al viewer con el `sessionId` del header STOMP | ✅ Pasa |

### `RaffleResultControllerTest` — cubre `RaffleResultController` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getRaffleResultByRaffleId_delegates` | Delega en el service con el `raffleId` del path | ✅ Pasa |
| `getLastRaffleResults_delegates` | Delega en el service | ✅ Pasa |
| `getDrawProofByRaffleId_delegates` | Delega en el service con el `raffleId` del path | ✅ Pasa |

### `RaffleWinnerControllerTest` — cubre `RaffleWinnerController` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getWonPrizes_delegates` | Extrae el `consumerId` del JWT y pasa el filtro de status | ✅ Pasa |
| `sendClaimPhoneOtp_returns202` | Delega en `TwilioSmsService` y responde 202 ACCEPTED | ✅ Pasa |
| `sendClaimEmailOtp_returns202` | Delega en `EmailVerificationService` y responde 202 ACCEPTED | ✅ Pasa |
| `claimPrize_returns204` | Delega con el `consumerId` del JWT y responde 204 NO_CONTENT | ✅ Pasa |

### `RaffleAdminControllerTest` — cubre `RaffleAdminController` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `prepareRaffleCreation_delegates` | Extrae el `adminId` del JWT y delega en `RaffleService` | ✅ Pasa |
| `confirmRaffleCreation_delegates` | Extrae el `adminId` del JWT, delega y responde 201 | ✅ Pasa |
| `updateRaffle_delegates` | Extrae el `adminId` del JWT y delega con el `raffleId` del path | ✅ Pasa |
| `activateRaffle_delegates` | Delega en `RaffleService` y responde 204 | ✅ Pasa |
| `closeRaffle_delegates` | Delega en `RaffleService` y responde 204 | ✅ Pasa |
| `cancelRaffle_delegates` | Delega en `RaffleService` y responde 204 | ✅ Pasa |
| `deleteRaffle_delegates` | Delega en `RaffleService` y responde 204 | ✅ Pasa |
| `conductDraw_delegates` | Delega en `DrawingService` con el `raffleId` del path | ✅ Pasa |
| `verifyDrawIntegrity_delegates` | Delega en `DrawingService` con el `raffleId` del path | ✅ Pasa |
| `countRafflesByStatus_delegates` | Delega en `RaffleService` con el status recibido | ✅ Pasa |
| `getTicketAuditLogs_delegates` | Delega en `RaffleTicketService` con el `ticketId` del path | ✅ Pasa |
| `getAuditLogsBetweenDates_delegates` | Delega con el rango de fechas y el pageable | ✅ Pasa |
| `getSuspiciousActivity_delegates` | Delega en `RaffleTicketService` con since/threshold | ✅ Pasa |
| `getRaffleStats_delegates` | Delega en `RaffleService` con el `raffleId` del path | ✅ Pasa |

---

## Services

### `RaffleServiceImplTest` — cubre `RaffleServiceImpl` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `draftWithPrizesNotExpired_activatesIt` | DRAFT con premios y sin expirar: pasa a ACTIVE | ✅ Pasa |
| `cancelledCanBeReactivated` | CANCELLED también puede reactivarse | ✅ Pasa |
| `wrongStatus_throwsInvalidOperationException` | Status distinto de DRAFT/CANCELLED: lanza `InvalidOperationException` | ✅ Pasa |
| `withoutPrizes_throwsInvalidOperationException` | Sin premios: lanza `InvalidOperationException` | ✅ Pasa |
| `expired_throwsInvalidOperationException` | Ya expiró (`endDate` en el pasado): lanza `InvalidOperationException` | ✅ Pasa |
| `closeRaffle_onlyFromActive` | ACTIVE pasa a CLOSED; cualquier otro status lanza `InvalidOperationException` | ✅ Pasa |
| `deleteRaffle_onlyFromDraft` | Solo permite borrar rifas en DRAFT | ✅ Pasa |
| `cancelRaffle_onlyFromActive` | Solo se puede invocar sobre una rifa ACTIVE | ✅ Pasa |
| `invalidId_throwsIllegalArgumentException` | `getRaffleById`: id inválido lanza `IllegalArgumentException` | ✅ Pasa |
| `notFound_throwsObjectNotFoundException` | `getRaffleById`: rifa inexistente lanza `ObjectNotFoundException` | ✅ Pasa |
| `invalidStatus_throwsIllegalArgumentException` | `getMyRafflesByStatus`: status distinto de ACTIVE/COMPLETED lanza excepción | ✅ Pasa |
| `invalidConsumerId_throwsIllegalArgumentException` | `getMyRafflesByStatus`: `consumerId` inválido lanza excepción | ✅ Pasa |
| `validDates_updatesFields` | `updateRaffle`: fechas válidas actualiza los campos | ✅ Pasa |
| `drawDateNotAfterEndDate_throwsInvalidRequestException` | `drawDate` no posterior a `endDate`: lanza excepción | ✅ Pasa |
| `endDateNotAfterStartDate_throwsInvalidRequestException` | `endDate` no posterior a `startDate`: lanza excepción | ✅ Pasa |
| `mismatchedPrizeImageCount_throwsInvalidRequestException` | `prepareRaffleCreation`: cantidad de imágenes ≠ cantidad de premios | ✅ Pasa |
| `duplicatePrizePositions_throwsInvalidRequestException` | Posiciones de premio duplicadas, antes de tocar storage | ✅ Pasa |
| `invalidDates_throwsInvalidRequestException` | `drawDate` no posterior a `endDate` en `prepareRaffleCreation` | ✅ Pasa |
| `tamperedRaffleData_throwsInvalidRequestException` | `confirmRaffleCreation`: datos alterados vs. lo usado en prepare | ✅ Pasa |
| `missingSnapshot_throwsInvalidRequestException` | Sin snapshot persistido en el asset | ✅ Pasa |

### `RaffleTicketServiceImplTest` — cubre `RaffleTicketServiceImpl` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `happyPath_issuesTickets` | Rifa activa, usuario elegible, sin límites: emite los tickets solicitados | ✅ Pasa |
| `invalidSourceId_throwsInvalidRequestException` | `sourceId` nulo o no positivo, antes de tocar la rifa | ✅ Pasa |
| `raffleNotActive_throwsInvalidRequestException` | Rifa que no está ACTIVE | ✅ Pasa |
| `raffleEnded_throwsInvalidRequestException` | Rifa ya finalizada (`endDate` pasado) | ✅ Pasa |
| `totalLimitReached_throwsLimitReachedException` | La rifa ya alcanzó el límite total de tickets | ✅ Pasa |
| `sourceLimitReached_throwsLimitReachedException` | La fuente ya alcanzó su límite específico | ✅ Pasa |
| `perUserLimitReached_throwsLimitReachedException` | El usuario ya alcanzó su límite por rifa | ✅ Pasa |
| `standardRaffle_alwaysEligible` | Rifa STANDARD: cualquier usuario es elegible, no consulta el consumidor | ✅ Pasa |
| `premiumRaffle_alwaysEligible` | Rifa PREMIUM: cualquier usuario es elegible | ✅ Pasa |
| `invalidId_throwsIllegalArgumentException` | `canUserReceiveTickets`: id inválido | ✅ Pasa |
| `expireTickets_invalidId_throwsIllegalArgumentException` | `expireTickets`: `raffleId` inválido | ✅ Pasa |

### `RaffleResultServiceImplTest` — cubre `RaffleResultServiceImpl` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getByRaffleId_invalidId_throwsIllegalArgumentException` | Id inválido lanza `IllegalArgumentException` | ✅ Pasa |
| `getByRaffleId_notFound_throwsObjectNotFoundException` | Sin resultado registrado lanza `ObjectNotFoundException` | ✅ Pasa |
| `getDrawProofByRaffleId_noProof_throwsInvalidOperationException` | Sin draw proof aún | ✅ Pasa |
| `getDrawProofByRaffleId_malformedJson_throwsInvalidOperationException` | JSON corrupto | ✅ Pasa |
| `getDrawProofByRaffleId_validJson_deserializesCorrectly` | JSON válido se deserializa correctamente al DTO | ✅ Pasa |

### `RaffleWinnerServiceImplTest` — cubre `RaffleWinnerServiceImpl` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `validEmailClaim_marksClaimedAndIncrementsCount` | Reclamo válido por email al correo registrado: marca reclamado e incrementa el contador | ✅ Pasa |
| `notTheWinner_throwsClaimPrizeException` | Usuario autenticado no es el ganador del premio | ✅ Pasa |
| `alreadyClaimed_throwsClaimPrizeException` | Premio ya reclamado | ✅ Pasa |
| `deadlinePassed_throwsClaimPrizeException` | Plazo de reclamación vencido | ✅ Pasa |
| `noWinnerRecord_throwsEntityNotFoundException` | No existe registro de ganador para el premio | ✅ Pasa |
| `newEmailWithoutOtp_throwsClaimPrizeException` | Correo alternativo sin código OTP | ✅ Pasa |
| `validSmsClaimWithNewPhone_deliversBySms` | Reclamo por SMS con teléfono alternativo y OTP válido: entrega por SMS | ✅ Pasa |
| `invalidSmsOtp_throwsClaimPrizeException` | SMS con OTP inválido: no entrega | ✅ Pasa |
| `expireOverduePrizes_marksExpiredAndReturnsCount` | Marca EXPIRED todos los premios pendientes vencidos y retorna la cantidad | ✅ Pasa |

### `RaffleRuleServiceImplTest` — cubre `RaffleRuleServiceImpl` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `found_returnsRule` | Regla encontrada: la retorna | ✅ Pasa |
| `notFound_throwsObjectNotFoundException` | Regla no configurada para esa rifa | ✅ Pasa |
| `invalidRaffleId_throwsIllegalArgumentException` | `raffleId` inválido | ✅ Pasa |

### `RaffleEventPublisherServiceImplTest` — cubre `RaffleEventPublisherServiceImpl` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `publishDrawingStarted_broadcastsToRaffleTopic` | Publica DRAWING_STARTED en el topic de la rifa | ✅ Pasa |
| `publishDrawCompleted_broadcastsAllWinners` | Publica DRAW_COMPLETED con todos los ganadores | ✅ Pasa |
| `publishWaitingRoomUpdate_broadcastsViewerCount` | Publica WAITING_ROOM_UPDATE con el conteo de espectadores | ✅ Pasa |
| `differentRaffles_publishToDifferentTopics` | Cada rifa publica en su propio topic, no se mezclan | ✅ Pasa |

### `DrawingServiceImplTest` — cubre `DrawingServiceImpl` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `selectsExactWinnersAndMarksThem` | `randomInternalDraw`: selecciona exactamente N tickets distintos y los marca ganadores | ✅ Pasa |
| `emptyTickets_throwsInvalidOperationException` | Lista de tickets vacía | ✅ Pasa |
| `nonPositiveWinners_throwsInvalidOperationException` | `numberOfWinners` no positivo | ✅ Pasa |
| `moreWinnersThanTickets_throwsInvalidOperationException` | Pide más ganadores que tickets disponibles | ✅ Pasa |
| `noProof_returnsFalse` | `verifyDrawIntegrity`: sin draw proof retorna false | ✅ Pasa |
| `notCompleted_returnsFalse` | Rifa no está COMPLETED | ✅ Pasa |
| `noWinners_returnsFalse` | Sin ganadores registrados | ✅ Pasa |
| `invalidJson_returnsFalse` | Draw proof con JSON inválido | ✅ Pasa |
| `allValid_returnsTrue` | Proof presente, COMPLETED, con ganadores y JSON válido | ✅ Pasa |
| `invalidId_throwsIllegalArgumentException` | `conductDraw`: `raffleId` inválido | ✅ Pasa |
| `notLive_throwsInvalidRaffleStatusException` | Rifa que no está LIVE | ✅ Pasa |
| `beforeDrawDate_throwsInvalidOperationException` | Aún no llega la fecha de sorteo | ✅ Pasa |
| `noDrawMethod_throwsInvalidOperationException` | Sin método de sorteo configurado | ✅ Pasa |
| `noPrizes_throwsInvalidOperationException` | Rifa sin premios | ✅ Pasa |
| `noActiveTickets_throwsInvalidInvocationException` | Rifa sin tickets activos | ✅ Pasa |
| `conductDraw_happyPath_systemRandom` | Camino feliz con SYSTEM_RANDOM: sortea, crea ganadores, genera proof y completa la rifa | ✅ Pasa |

### `RandomOrgServiceImplTest` — cubre `RandomOrgServiceImpl` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `negativeMin_throwsIllegalArgumentException` | `min` negativo | ✅ Pasa |
| `maxNotGreaterThanMin_throwsIllegalArgumentException` | `max` no mayor que `min` | ✅ Pasa |
| `nonPositiveCount_throwsIllegalArgumentException` | `count` no positivo | ✅ Pasa |
| `countExceedsRange_throwsIllegalArgumentException` | `count` mayor al tamaño del rango | ✅ Pasa |
| `countAboveHardLimit_throwsIllegalArgumentException` | `count` mayor a 10.000 | ✅ Pasa |
| `success_returnsIndicesAndMetadata` | Respuesta exitosa: retorna los índices y la metadata de la firma | ✅ Pasa |
| `businessError_throwsRandomOrgException` | Random.org retorna un error de negocio en el body | ✅ Pasa |
| `emptyBody_throwsRandomOrgException` | Body vacío | ✅ Pasa |
| `networkFailure_throwsRandomOrgException` | Falla de red/conexión | ✅ Pasa |

### `TicketDeliveryServiceImplTest` — cubre `TicketDeliveryServiceImpl` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `meetsMinimum_issuesTicketsAndNotifies` | Compra cumple el monto mínimo: emite tickets y notifica | ✅ Pasa |
| `belowMinimum_doesNotIssueTickets` | Compra no alcanza el monto mínimo | ✅ Pasa |
| `alreadyIssuedForPurchase_isIdempotent` | Ya se emitieron tickets para esta compra: no repite | ✅ Pasa |
| `noActiveRaffles_returnsEmptyResult` | Sin rifas activas: resultado vacío, sin tocar idempotencia | ✅ Pasa |
| `invalidInputs_throwInvalidRequestException` | Parámetros inválidos | ✅ Pasa |
| `firstLoginToday_awardsTicketAndUpdatesDate` | Primer login del día: otorga el ticket y actualiza fecha | ✅ Pasa |
| `alreadyAwardedToday_skips` | Ya se otorgó el bono hoy: no repite | ✅ Pasa |
| `noConsumerDetails_doesNothingSilently` | Sin `ConsumerDetails` para el usuario: no falla | ✅ Pasa |
| `invalidConsumerId_throwsInvalidRequestException` | `consumerId` inválido | ✅ Pasa |
| `alreadyExists_isIdempotent` | Referido: ya existe un ticket para este referido | ✅ Pasa |
| `noActiveRaffles_awardsDoubleXpInstead` | Sin rifas activas: otorga XP doble al referidor | ✅ Pasa |
| `withActiveRaffleAndRule_issuesTicketsByLevel` | Con rifa activa y regla REFERRAL: emite según nivel del referidor | ✅ Pasa |
| `lowLevelSkipsPremiumRaffle` | Nivel bajo (BRONCE) no accede a rifa PREMIUM: la salta | ✅ Pasa |
| `invalidIds_throwInvalidRequestException` | Ids inválidos | ✅ Pasa |

### `TicketEarningRuleServiceImplTest` — cubre `TicketEarningRuleServiceImpl` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `purchaseRuleWithMinAmount_createsSuccessfully` | Regla PURCHASE con monto mínimo se crea correctamente | ✅ Pasa |
| `duplicateName_throwsDuplicateResourceException` | Nombre de regla duplicado | ✅ Pasa |
| `purchaseRuleWithoutMinAmount_throwsInvalidRequestException` | Regla PURCHASE sin monto mínimo | ✅ Pasa |
| `dailyLoginRuleWithoutFlag_throwsInvalidRequestException` | Regla DAILY_LOGIN sin marcar `dailyLogin=true` | ✅ Pasa |
| `newNameUsedByAnotherRule_throwsDuplicateResourceException` | Nuevo nombre ya usado por otra regla | ✅ Pasa |
| `keepingSameName_isNotADuplicate` | Mantener el mismo nombre no cuenta como duplicado | ✅ Pasa |
| `delete_associatedWithRaffles_throwsInvalidRequestException` | Si está asociada a rifas, no se puede eliminar | ✅ Pasa |
| `delete_notInUse_deletesIt` | Sin uso, se elimina sin problema | ✅ Pasa |
| `activateAndDeactivate_toggleActiveFlag` | Activar/desactivar cambia el flag `isActive` | ✅ Pasa |
| `getById_validations` | Id inválido lanza `IllegalArgumentException`; inexistente lanza `ObjectNotFoundException` | ✅ Pasa |

### `WaitingRoomServiceImplTest` — cubre `WaitingRoomServiceImpl` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `addViewer_incrementsCount` | Suma un espectador a la sala de la rifa | ✅ Pasa |
| `addViewer_sameSessionTwice_doesNotDuplicate` | Misma sesión dos veces: no duplica (es un Set) | ✅ Pasa |
| `removeViewer_decrementsCount` | Resta un espectador de la sala | ✅ Pasa |
| `removeViewerFromAllRooms_removesFromCorrectRoom` | Quita la sesión de la sala en la que estaba, sin saber el `raffleId` | ✅ Pasa |
| `getViewerCount_zeroForUnknownRaffle` | 0 para una rifa sin espectadores conectados | ✅ Pasa |

### `RaffleDrawStateCacheTest` — cubre `RaffleDrawStateCache` — **Nuevo**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `replacesState` | `onDrawingStarted` reemplaza completamente el estado: fase DRAWING_STARTED, ganadores vacíos | ✅ Pasa |
| `withPriorState_addsWinnerAndChangesPhase` | `onWinnerRevealed` con estado previo: agrega el ganador y cambia a WINNER_REVEALED | ✅ Pasa |
| `withoutPriorState_isSilentNoOp` (en `onWinnerRevealed`) | Sin estado previo: no-op silencioso, sin excepción | ✅ Pasa |
| `withPriorState_changesPhase` (en `onDrawCompleted`) | Con estado previo: cambia la fase a DRAW_COMPLETED | ✅ Pasa |
| `withoutPriorState_isSilentNoOp` (en `onDrawCompleted`) | Sin estado previo: no-op silencioso | ✅ Pasa |
| `withPriorState_changesPhase` (en `onDrawError`) | Con estado previo: cambia la fase a DRAW_ERROR | ✅ Pasa |
| `withoutPriorState_isSilentNoOp` (en `onDrawError`) | Sin estado previo: no-op silencioso | ✅ Pasa |
| `evictRemovesState` | `exists` refleja si hay estado; `evict` lo elimina | ✅ Pasa |
| `evictWithoutState_doesNotThrow` | `evict` sobre rifa sin estado no lanza excepción | ✅ Pasa |
| `withoutState_returnsWaitingRoom` | `buildStatus` sin estado: WAITING_ROOM_UPDATE, `totalParticipants` = parámetro | ✅ Pasa |
| `withState_ignoresTotalParticipantsParameter` | `buildStatus` con estado: `totalParticipants` queda `null` (hallazgo documentado) | ✅ Pasa |
| `raffleParameterIsUnused` | El parámetro `raffle` no se usa: pasar `null` no afecta el resultado | ✅ Pasa |
| `concurrentWinnerReveals_neverThrows_andDocumentsPossibleLostUpdates` | N hilos revelando ganadores a la vez: no lanza excepción; documenta riesgo de pérdida por `ArrayList` no sincronizada | ✅ Pasa |

---

## Mappers MapStruct (implementación real generada, sin mocks) — todas **Nuevas**

Ubicación: `src/test/java/com/verygana2/mappers/raffles/`

### `PrizeMapperTest` — cubre `PrizeMapper`

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `mapsEditableFields` (en `toPrize`) | Mapea campos editables del request; deja en null los gestionados por el service | ✅ Pasa |
| `withImageAsset_usesCdnPrefix` | Con `imageAsset`: `imageUrl` usa el prefijo CDN sobre el `objectKey` | ✅ Pasa |
| `withoutImageAsset_imageUrlIsNull` | Sin `imageAsset`: `imageUrl` es `null` (null-safe) | ✅ Pasa |

### `RaffleMapperTest` — cubre `RaffleMapper`

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `datesInOtherZone_areNormalizedToUtc` | Fechas en otra zona horaria se normalizan a UTC preservando el instante | ✅ Pasa |
| `nullDates_doNotThrow` | Fechas null no lanzan NPE en `normalizeDatesToUTC` | ✅ Pasa |
| `imageUrl_isRawObjectKey_withoutCdnPrefix` | `toPrizeResponseDTO(Prize)`: `imageUrl` sale directo del `objectKey`, sin prefijo CDN (difiere de `PrizeMapper`) | ✅ Pasa |
| `withoutImageAsset_imageUrlIsNull` | Sin `imageAsset`: `imageUrl` null, sin NPE | ✅ Pasa |
| `raffleId_comesFromRaffleRuleOwnId` | `toRaffleResponseDTO(RaffleRule)`: `raffleId` sale del id de la propia regla (hallazgo documentado) | ✅ Pasa |
| `onlyIdIsMapped` | `toRaffleStatsResponseDTO`: solo mapea `id`, resto queda por defecto | ✅ Pasa |
| `nonEmptyList_returnsSize` | `getPrizeCount`: lista no vacía devuelve su tamaño | ✅ Pasa |
| `emptyList_returnsZero` | Lista vacía devuelve 0 | ✅ Pasa |
| `nullList_returnsZero` | `prizes == null` devuelve 0 | ✅ Pasa |

### `RaffleResultMapperTest` — cubre `RaffleResultMapper`

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `mapsFields` | `toRaffleSummaryResultResponseDTO`: mapea `raffleId`/`raffleTitle`/`raffleType`/`drawnAt` | ✅ Pasa |
| `withImageAsset_usesCdnPrefix` (raffle) | Con `imageAsset`: `raffleImageUrl` usa prefijo CDN | ✅ Pasa |
| `withoutImageAsset_producesLiteralNullString` (raffle) | Sin `imageAsset`: produce el literal `".../public/null"` (no null-safe, hallazgo documentado) | ✅ Pasa |
| `nullRaffle_doesNotThrow` | `raffleResult.raffle == null`: no lanza NPE | ✅ Pasa |
| `withImageAsset_usesCdnPrefix` (prize) | `toWinnerDetailDTO` con `imageAsset`: prefijo CDN | ✅ Pasa |
| `withoutImageAsset_producesLiteralNullString` (prize) | Mismo comportamiento no null-safe en `prizeImageUrl` | ✅ Pasa |
| `nullWinner_doesNotThrow` | `winner == null`: no lanza NPE | ✅ Pasa |

### `RaffleTicketMapperTest` — cubre `RaffleTicketMapper`

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `mapsFieldsIncludingRaffleId` | `raffleId` sale de `raffle.id`, resto directo | ✅ Pasa |
| `nullRaffle_doesNotThrow` | `raffle == null`: `raffleId` queda null, sin NPE | ✅ Pasa |

### `RaffleWinnerMapperTest` — cubre `RaffleWinnerMapper`

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `happyPath_mapsRaffleTitleThroughTwoHops` | `raffleTitle` sale de `winner.raffleResult.raffle.title` (2 saltos) | ✅ Pasa |
| `nullRaffleResult_doesNotThrow` | `raffleResult == null`: no lanza NPE | ✅ Pasa |
| `nullRaffleInsideResult_doesNotThrow` | `raffleResult.raffle == null`: no lanza NPE | ✅ Pasa |
| `nullPrize_doesNotThrow` | `prize == null`: no lanza NPE | ✅ Pasa |

### `TicketAuditLogMapperTest` — cubre `TicketAuditLogMapper`

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `mapsFieldsIncludingTicketId` | `ticketId` sale de `ticket.id`, resto directo | ✅ Pasa |
| `nullTicket_doesNotThrow` | `ticket == null`: `ticketId` null, sin NPE | ✅ Pasa |

### `TicketEarningRuleMapperTest` — cubre `TicketEarningRuleMapper`

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `minPurchaseAmountCents_convertsLongToEquivalentBigDecimal` | `Long` se convierte a `BigDecimal` numéricamente equivalente | ✅ Pasa |
| `nullMinPurchaseAmountCents_staysNull` | `null` no setea nada, queda `null` en el DTO | ✅ Pasa |
| `mapsRemainingFields` | Mapea el resto de campos directamente | ✅ Pasa |

---

## Modelos / entidades (lógica de dominio sin BD)

### `RaffleTest` — cubre `Raffle` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `hasReachedTotalLimit_noLimitConfigured_neverReached` | Sin límite configurado, nunca se considera alcanzado | ✅ Pasa |
| `hasReachedTotalLimit_trueWhenIssuedReachesMax` | `true` cuando los emitidos igualan o superan el máximo | ✅ Pasa |
| `incrementTicketCount_addsQuantity` | Suma la cantidad indicada al contador | ✅ Pasa |
| `incrementTicketCount_nonPositiveQuantity_throws` | Cantidad no positiva lanza `IllegalArgumentException` | ✅ Pasa |
| `incrementParticipantCount_addsOne` | Suma 1 al contador de participantes | ✅ Pasa |
| `onCreate_defaultsToDraftWithZeroCounters` | Hook `@PrePersist`: nace en DRAFT con contadores en cero | ✅ Pasa |

### `PrizeTest` — cubre `Prize` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `incrementClaimedCount_reachesQuantity_movesToDelivered` | Suma 1, y si llega a la cantidad total pasa a DELIVERED | ✅ Pasa |
| `incrementClaimedCount_belowQuantity_statusUnchanged` | Si aún faltan unidades, el status no cambia | ✅ Pasa |
| `incrementClaimedCount_nullClaimedCount_startsAtOne` | Si `claimedCount` venía null, arranca en 1 | ✅ Pasa |
| `getImageUrl_dependsOnImageAsset` | Sin `imageAsset` retorna null; con `imageAsset` arma la URL del CDN | ✅ Pasa |
| `onCreate_defaultsToPendingWithZeroClaims` | Hook `@PrePersist`: nace PENDING con `claimedCount` en cero | ✅ Pasa |

### `RaffleRuleTest` — cubre `RaffleRule` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `inactiveConfig_cannotIssue` | Config inactiva: no puede emitir aunque la regla global esté activa | ✅ Pasa |
| `inactiveGlobalRule_cannotIssue` | Regla global inactiva: no puede emitir aunque la config esté activa | ✅ Pasa |
| `noSourceLimit_alwaysCanIssue` | Sin límite de fuente configurado: siempre puede emitir | ✅ Pasa |
| `withSourceLimit_respectsLimit` | Con límite: puede emitir mientras no lo supere, y no al superarlo | ✅ Pasa |
| `getRemainingTickets_computesCorrectly` | Null si no hay límite; o el restante (nunca negativo) si lo hay | ✅ Pasa |
| `incrementIssuedCount_addsQuantity` | Suma la cantidad indicada | ✅ Pasa |
| `incrementIssuedCount_nonPositiveQuantity_throws` | Cantidad no positiva lanza `IllegalArgumentException` | ✅ Pasa |

---

## Resumen

- **Clases de test**: 29 (6 controllers + 12 services incl. cache + 7 mappers + 3 modelos + 1 sin contar aparte).
- **Pruebas individuales**: ver total agregado del dominio completo (309, incluyendo integración y repositorio) al pie de `RAFFLES_INTEGRATION_TESTS.md`.
- **Resultado**: ✅ todas pasan — 0 fallos, 0 errores (`mvn test`, 2026-08-26).
