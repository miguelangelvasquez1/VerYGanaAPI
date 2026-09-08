# Pruebas de repositorio — Dominio PQRS

Inventario completo de las pruebas de repositorio nuevas del dominio `PQRS` (`@DataJpaTest`, H2 en memoria modo MySQL). Antes de este trabajo no existía ningún test de repositorio dedicado. Resultado confirmado corriendo `mvn test` el 2026-08-27 junto con el resto del lote de PQRS: **45 pruebas nuevas, 0 fallos, 0 errores, BUILD SUCCESS**.

Todas las clases de este documento son **Nuevas**. Ubicación: `src/test/java/com/verygana2/repositories/pqrs/`.

---

## `PqrsRepositoryTest` — cubre `PqrsRepository` — 11 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsOnlyPqrsOfGivenRequester` | `findByRequesterId`: solo los PQRS del requester dado | ✅ Pasa |
| `noFiltersReturnsAllAssignedToAdmin` | `findByAssignedAdminWithFilters` sin filtros: todos los asignados al admin | ✅ Pasa |
| `filtersByStatus` | Filtra por status | ✅ Pasa |
| `filtersByType` | Filtra por type | ✅ Pasa |
| `combinesStatusAndType` | Combina status y type | ✅ Pasa |
| `returnsExactMatchOfStatus` | `findByStatus`: derived query, coincidencia exacta | ✅ Pasa |
| `returnsMatchingStatusAndOverdueDueDate` | `findByStatusInAndDueDateBefore`: status en la lista Y `dueDate` vencido | ✅ Pasa |
| `excludesPqrsFailingEitherCriterion` | Excluye los que no cumplen alguno de los dos criterios | ✅ Pasa |
| `trueWhenOpenPqrsExistsForItem` | `existsOpenByPurchaseItemId`: true con un PQRS no-terminal (ej. RECIBIDA) sobre ese ítem | ✅ Pasa |
| `falseWhenOnlyPqrsIsResolvedOrClosed` | False si el único PQRS del ítem está RESUELTA o CERRADA | ✅ Pasa |
| `falseWhenNoPqrsForItem` | False si no hay ningún PQRS para ese ítem | ✅ Pasa |

## `PqrsAssetRepositoryTest` — cubre `PqrsAssetRepository` — 6 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsExactlyRequestedIdsIgnoringMissingOnes` | `findAllByIdIn`: trae exactamente los ids pedidos, ignora los inexistentes | ✅ Pasa |
| `orphanedExpiredAssetAppears` | `findDeletableAssets`: asset ORPHANED vencido aparece | ✅ Pasa |
| `pendingExpiredAssetAppears` | Asset PENDING vencido aparece | ✅ Pasa |
| `validatedUnclaimedExpiredAssetAppears` | Asset VALIDATED nunca reclamado (`pqrs=null`) y vencido aparece | ✅ Pasa |
| `validatedClaimedExpiredAssetDoesNotAppear` | **Caso delicado**: asset VALIDATED ya reclamado (`pqrs` seteado) NO aparece aunque esté vencido | ✅ Pasa |
| `assetNotYetExpiredNeverAppearsRegardlessOfStatus` | Cualquier asset con `createdAt >= threshold` no aparece sin importar el status | ✅ Pasa |

---

## Resumen

- **Clases de test**: 2 (todas nuevas — primera cobertura de repositorio de todo el dominio PQRS).
- **Pruebas individuales de esta categoría**: 17.
- **Total del dominio PQRS de esta ronda**: 45 pruebas nuevas, 0 fallos, 0 errores — `mvn test`, 2026-08-27.

---

# Cierre del ciclo de auditoría de los 4 dominios

Con PQRS completo, quedan cerrados los cuatro dominios auditados originalmente:

| Dominio | Archivos de test nuevos | Pruebas nuevas |
|---|---|---|
| Raffles | 21 | ~309 (dominio completo, incl. existentes) |
| Marketplace | 18 | 188 |
| Finance | 46 | 395 |
| PQRS | 5 | 45 |

Bugs de producción reales encontrados y documentados (no corregidos, por decisión explícita): `ProductRepository.searchProductsInternal` excluye productos sin `TargetAudience` (marketplace); comportamientos no null-safe en `MoneyMapper`/`RaffleResultMapper` (finance/raffles); inconsistencias de mapeo entre métodos "gemelos" en varios mappers. Todos quedan listados con su ubicación exacta en los documentos de cada dominio.
