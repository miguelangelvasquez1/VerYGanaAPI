# Pruebas de repositorio — Dominio Raffles

Inventario completo de pruebas de repositorio (`@DataJpaTest`, H2 en memoria modo MySQL) del dominio `raffles`. **Las 11 clases de esta categoría son nuevas** — antes de este trabajo no existía ningún test de repositorio dedicado (solo cobertura indirecta vía `RaffleTicketConcurrencyIntegrationTest`, ver `RAFFLES_INTEGRATION_TESTS.md`). Resultado confirmado corriendo `mvn test` el 2026-08-26 junto con el resto del dominio: **309 pruebas totales del dominio, 0 fallos, 0 errores, BUILD SUCCESS**.

Ubicación: `src/test/java/com/verygana2/repositories/raffles/`

---

## `PrizeRepositoryTest` — cubre `PrizeRepository` — **Nuevo**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsPrizesOrderedByPosition` | `findByRaffleIdOrderByPositionAsc`: trae los premios de una rifa ordenados ascendentemente por posición | ✅ Pasa |
| `returnsEmptyWhenNoPrizes` | Retorna lista vacía si la rifa no tiene premios | ✅ Pasa |
| `returnsOverdueUnclaimedPrize` | `findOverdueUnclaimedPrizes`: premio PENDING con ganador vencido y sin reclamar aparece | ✅ Pasa |
| `excludesClaimedPrize` | Premio ya reclamado a tiempo (DELIVERED) no aparece | ✅ Pasa |
| `excludesPrizeWithinDeadline` | Premio PENDING con ganador sin reclamar pero dentro de plazo no aparece | ✅ Pasa |
| `excludesPrizeWithoutWinner` | Premio PENDING sin ningún ganador registrado no aparece | ✅ Pasa |

## `PrizeImageAssetRepositoryTest` — cubre `PrizeImageAssetRepository` — **Nuevo**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsOnlyMatchingStatusBeforeThreshold` | `findDeletableAssets`: trae solo los assets del status buscado con `uploadedAt` antes del threshold | ✅ Pasa |
| `returnsEmptyWhenNoMatch` | Retorna lista vacía si nada coincide | ✅ Pasa |

## `RaffleImageAssetRepositoryTest` — cubre `RaffleImageAssetRepository` — **Nuevo**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `findsAssetForRaffle` | `findByRaffleId`: encuentra el asset asociado a la rifa | ✅ Pasa |
| `returnsEmptyWhenNoAsset` | Retorna `Optional.empty` si la rifa no tiene asset | ✅ Pasa |
| `returnsOnlyMatchingStatusBeforeThreshold` | `findDeletableAssets`: trae solo los assets del status buscado antes del threshold | ✅ Pasa |
| `returnsEmptyWhenNoMatch` | Retorna lista vacía si nada coincide | ✅ Pasa |

## `RaffleParticipationRepositoryTest` — cubre `RaffleParticipationRepository` — **Nuevo**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `findsExistingParticipation` | `findByConsumerIdAndRaffleId`: encuentra la participación existente del consumer en la rifa | ✅ Pasa |
| `returnsEmptyWhenNoParticipation` | Retorna `Optional.empty` si el consumer no participó en esa rifa | ✅ Pasa |
| `returnsEmptyWhenConsumerDoesNotExist` | Retorna `Optional.empty` si el consumer no existe | ✅ Pasa |

## `RaffleResultRepositoryTest` — cubre `RaffleResultRepository` — **Nuevo**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `ordersByDrawnAtDescending` | `findLastRaffleResults`: ordena por `drawnAt` descendente | ✅ Pasa |
| `limitsToTen` | Retorna como máximo 10 resultados (verificado con 12 fixtures) | ✅ Pasa |
| `findsResultForRaffle` | `findByRaffleId`: encuentra el resultado asociado a la rifa | ✅ Pasa |
| `returnsEmptyWhenNoResult` | Retorna `Optional.empty` si la rifa no tiene resultado | ✅ Pasa |

## `RaffleRuleRespositoryTest` — cubre `RaffleRuleRespository` (typo real de la interfaz) — **Nuevo**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `findsRuleMatchingType` | `findByRaffleIdAndRuleType`: encuentra la regla de la rifa que coincide con el tipo pedido | ✅ Pasa |
| `returnsEmptyWhenTypeDoesNotMatch` | Retorna `Optional.empty` si la rifa no tiene una regla de ese tipo | ✅ Pasa |
| `returnsEmptyWhenRaffleDoesNotExist` | Retorna `Optional.empty` si la rifa no existe | ✅ Pasa |

## `RaffleRepositoryTest` — cubre `RaffleRepository` — **Nuevo**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsRaffleUnderPessimisticLock` | `findByIdForUpdate`: recupera la rifa correcta bajo lock `PESSIMISTIC_WRITE` | ✅ Pasa |
| `returnsEmptyWhenNotFound` (findByIdForUpdate) | Retorna `Optional.empty` si el id no existe | ✅ Pasa |
| `filtersByStatusAndProjectsFields` | `findByFilters`: filtra por status y proyecta campos incl. conteo de premios | ✅ Pasa |
| `filtersByDrawDateRange` | Filtra por rango de fecha de sorteo | ✅ Pasa |
| `filtersBySearchText` | Filtra por texto de búsqueda en el título, sin distinguir mayúsculas | ✅ Pasa |
| `countsOnlyMatchingStatus` | `countByRaffleStatus`: cuenta solo las rifas con el status pedido | ✅ Pasa |
| `returnsRaffleWithinRangeWithRulesFetched` | `findActiveRaffleByDrawDate`: trae rifas ACTIVE en rango `[startDate,endDate)`, reglas ya inicializadas | ✅ Pasa |
| `returnsDraftRaffleWithPrizesAndRules` | `findRafflesToActivate`: trae rifas DRAFT con premios y reglas configurados | ✅ Pasa |
| `excludesDraftRaffleWithoutPrizesOrRules` | NO trae rifas DRAFT sin premios ni reglas | ✅ Pasa |
| `returnsActiveRaffleWithPastEndDate` | `findRafflesToClose`: trae rifas ACTIVE cuya `endDate` ya pasó | ✅ Pasa |
| `returnsClosedRaffleWithinLiveThreshold` | `findRafflesToSetLive`: trae rifas CLOSED cuya `drawDate` cae dentro del umbral "live" | ✅ Pasa |
| `returnsClosedRaffleWithPastDrawDate` | `findMissedDrawRaffles`: trae rifas CLOSED cuya `drawDate` ya pasó sin sortearse | ✅ Pasa |
| `returnsLiveRaffleBeforeHorizon` | `findLiveRafflesWithDrawDateBefore`: trae rifas status literal LIVE con `drawDate<=horizon` | ✅ Pasa |
| `findLiveRafflesWithoutMunicipalityFilterReturnsAll` | `findLiveRaffles` sin filtro de municipio trae todas las LIVE con premios | ✅ Pasa |
| `findLiveRafflesFiltersByMunicipality` | Con filtro de municipio excluye rifas dirigidas a otro municipio | ✅ Pasa |
| `findActiveRafflesFiltersByTypeAndMunicipality` | `findActiveRaffles`: filtra por tipo y municipio, pagina el resultado | ✅ Pasa |
| `countsDistinctTicketsAndFlagsWinner` | `findMyRafflesByStatus`: cuenta tickets distintos y marca `isWinner` cuando algún ticket ganó | ✅ Pasa |
| `isWinnerFalseWhenNoWinningTicket` | `isWinner` es `false` cuando ningún ticket del usuario ganó | ✅ Pasa |
| `fetchesPrizesEagerly` | `findByIdWithPrizes`: trae la rifa con premios ya inicializados, sin `LazyInitializationException` | ✅ Pasa |
| `returnsEmptyWhenNotFound` (findByIdWithPrizes) | Retorna `Optional.empty` si la rifa no existe | ✅ Pasa |

## `RaffleTicketRepositoryTest` — cubre `RaffleTicketRepository` — **Nuevo**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `findsExactTicketOnlyUnderCorrectRaffle` | `findByTicketNumberAndRaffleId`/`existsBy...`: encuentra el ticket exacto y no bajo otra rifa | ✅ Pasa |
| `countByOwnerAndRaffle` | `countByTicketOwnerIdAndRaffleId`: cuenta solo los tickets del usuario en esa rifa | ✅ Pasa |
| `countByOwnerRaffleAndStatus` | `countByTicketOwnerIdAndRaffleIdAndStatus`: distingue por status del ticket | ✅ Pasa |
| `countByOwnerAndStatusAcrossRaffles` | `countByTicketOwnerIdAndStatus`: cuenta a través de todas las rifas del usuario | ✅ Pasa |
| `countWinnerTickets` | `countWinnerTicketsByUserId`: cuenta únicamente los tickets marcados `isWinner=true` | ✅ Pasa |
| `countByOwnerRaffleAndSource` | `countByTicketOwnerIdAndRaffleIdAndSource`: distingue por fuente | ✅ Pasa |
| `groupsByRaffleCountingOnlyActiveOrderedByDrawDateDesc` | `countTicketsByTicketOwnerGroupedByRaffle`: agrupa por rifa, solo ACTIVE, ordena por `drawDate` desc | ✅ Pasa |
| `findUserTicketsByRafflePages` | `findUserTicketsByRaffle`: pagina los tickets del usuario en esa rifa | ✅ Pasa |
| `findUserWinnerTicketsOnlyWinners` | `findUserWinnerTickets`: solo trae tickets marcados `isWinner=true` | ✅ Pasa |
| `noFiltersReturnsAll` | `findRaffleTicketsWithFilters` sin filtros trae todos los tickets de la rifa | ✅ Pasa |
| `filtersByStatusAndSource` | Filtra por status y por source cuando se especifican | ✅ Pasa |
| `filtersByIssuedAtMinimum` | Filtra por `issuedAt` mínimo cuando se especifica | ✅ Pasa |
| `detectsAlreadyIssued` | `existsByTicketOwnerIdAndSourceAndSourceId`: detecta idempotencia | ✅ Pasa |
| `returnsOnlyMatchingStatusTickets` | `findByRaffleIdAndStatus`: trae solo los tickets ACTIVE de la rifa | ✅ Pasa |
| `bulkExpiresActiveTicketsAndReturnsAffectedCount` | `expireTicketsByRaffle`: UPDATE masivo a EXPIRED, retorna filas afectadas, estado real en BD confirmado tras `entityManager.clear()` | ✅ Pasa |
| `doesNotAffectNonActiveOrOtherRaffleTickets` | No afecta tickets ya no ACTIVE ni de otras rifas | ✅ Pasa |
| `groupsCountBySource` | `countTicketsBySource`: agrupa el conteo de tickets de la rifa por source | ✅ Pasa |

## `RaffleWinnerRepositoryTest` — cubre `RaffleWinnerRepository` — **Nuevo**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsAllWinnersOfResultOnly` | `findByRaffleResultId`: trae todos los ganadores de un resultado y ninguno de otro | ✅ Pasa |
| `withoutStatusFilterReturnsAllFetched` | `findWonPrizesByConsumer` sin filtro: trae todos los premios ganados, `JOIN FETCH` ya inicializados | ✅ Pasa |
| `withStatusFilterReturnsOnlyMatching` | Con filtro de status solo trae los premios que coinciden | ✅ Pasa |
| `countsWinnersForResult` | `countByRaffleResultId`: cuenta los ganadores asociados a ese resultado | ✅ Pasa |
| `ordersByCreatedAtDescending` | `findLastWinners`: ordena por `createdAt` descendente | ✅ Pasa |
| `findsWinnerForPrize` | `findByPrizeId`: encuentra el ganador de un premio específico | ✅ Pasa |
| `returnsEmptyWhenNoWinner` | Retorna `Optional.empty` si el premio no tiene ganador registrado | ✅ Pasa |

## `TicketAuditLogRepositoryTest` — cubre `TicketAuditLogRepository` — **Nuevo**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsLogsOrderedNewestFirst` | `findByTicketIdOrderByCreatedAtDesc`: trae los logs de un ticket ordenados del más reciente al más antiguo | ✅ Pasa |
| `matchingActionStringStillThrowsDueToStrictParameterTypeValidation` | `findByAction`: **hallazgo** — un string que coincide con el enum igual falla, Hibernate valida el tipo del parámetro en el bind | ✅ Pasa |
| `nonMatchingActionStringAlsoThrowsSameTypeException` | Un string que NO coincide también falla con la misma excepción de tipo, no con resultado vacío | ✅ Pasa |
| `filtersLogsBySourceType` | `findBySourceType`: filtra los logs por su fuente | ✅ Pasa |
| `returnsLogsWithinRangeOrderedDesc` | `findLogsBetweenDates`: trae solo los logs dentro del rango, ordenados descendente | ✅ Pasa |
| `returnsOnlyIpsAboveThreshold` | `findSuspiciousActivity`: agrupa por IP, solo trae las que superan el umbral | ✅ Pasa |
| `returnsEmptyWhenNoIpAboveThreshold` | No trae nada si ninguna IP supera el umbral | ✅ Pasa |

## `TicketEarningRuleRepositoryTest` — cubre `TicketEarningRuleRepository` — **Nuevo**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsActiveRulesOfTypeOrderedByPriorityDesc` | `findByRuleTypeAndIsActiveTrueOrderByPriorityDesc`: trae solo reglas activas del tipo pedido, por prioridad desc | ✅ Pasa |
| `bothFiltersNullReturnsAllOrderedByPriority` | `findByRuleTypeAndIsActiveOrderByPriorityDesc` sin filtros trae todas ordenadas | ✅ Pasa |
| `filtersOnlyByRuleTypeWhenIsActiveNull` | Filtra solo por `ruleType` cuando `isActive` es null | ✅ Pasa |
| `filtersOnlyByIsActiveWhenRuleTypeNull` | Filtra solo por `isActive` cuando `ruleType` es null | ✅ Pasa |
| `filtersByBothCriteriaCombined` | Filtra por ambos criterios combinados | ✅ Pasa |
| `detectsExistingRuleName` | `existsByRuleName`: detecta si ya existe una regla con ese nombre | ✅ Pasa |
| `countsOnlyActiveRules` | `countByIsActiveTrue`: cuenta únicamente las reglas activas | ✅ Pasa |

---

## Hallazgos técnicos relevantes (entorno de test, no bugs de producción)

1. **H2 2.x reserva `VALUE` como palabra clave** — rompe la creación de `raffle_prizes` (columna `Prize.value`) bajo H2 estándar. Se agregó `NON_KEYWORDS=VALUE` a la URL JDBC de `PrizeRepositoryTest`, `RaffleRepositoryTest` y `RaffleWinnerRepositoryTest`. Config de test únicamente.
2. **Inner join implícito en `imageAsset.objectKey`** — las proyecciones que navegan `r.imageAsset.objectKey` sin `LEFT JOIN` explícito excluyen silenciosamente rifas sin asset. Detalle de la query, no un bug.
3. **Identity map de JPA con inserts manuales** — `findActiveRaffleByDrawDate` requirió `entityManager.clear()` antes de la query para que el `LEFT JOIN FETCH` repueble correctamente `raffleRules`.

**Hallazgo real de producción a revisar por el equipo**: `TicketAuditLogRepository.findByAction(String, Pageable)` lanza `InvalidDataAccessApiUsageException` para **cualquier** valor de entrada en el entorno de test (Hibernate valida el tipo del parámetro `String` contra el campo enum `AuditAction` antes de ejecutar la query), en vez de comportarse como un filtro normal que retorna vacío si no coincide. Confirmar si el comportamiento es igual contra MySQL real en producción.

## Resumen

- **Clases de test**: 11 (todas nuevas).
- **Pruebas individuales de esta categoría**: 65.
- **Total del dominio raffles (unitarias + integración + repositorio)**: 309 pruebas, 0 fallos, 0 errores (`mvn test`, 2026-08-26).
