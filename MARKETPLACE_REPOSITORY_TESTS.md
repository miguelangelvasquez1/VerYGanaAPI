# Pruebas de repositorio — Dominio Marketplace

Inventario completo de las pruebas de repositorio nuevas del dominio `marketplace` (`@DataJpaTest`, H2 en memoria modo MySQL). Antes de este trabajo no existía ningún test de repositorio dedicado en el dominio. Resultado confirmado corriendo `mvn test` el 2026-08-26 junto con el resto del lote de marketplace: **188 pruebas totales del dominio, 0 fallos, 0 errores, BUILD SUCCESS**.

Todas las clases de este documento son **Nuevas**. Ubicación: `src/test/java/com/verygana2/repositories/marketplace/`.

---

## ⚠️ Bug de producción encontrado (prioridad alta, no corregido)

**`ProductRepository.searchProductsInternal` excluye silenciosamente productos sin `TargetAudience` asignado.** La query navega `p.targetAudience.targetMunicipalities` dentro del `ORDER BY` sin un `LEFT JOIN` explícito (a diferencia de `RaffleRepository`, que sí hace `LEFT JOIN r.targetAudience ta` correctamente). Hibernate resuelve esa navegación implícita como **INNER JOIN**, así que **cualquier producto sin `TargetAudience` — el caso más común, ya que el campo es opcional — desaparece de todos los resultados de búsqueda**, con o sin filtro de municipio. Esto contradice el propio Javadoc del método ("el municipio solo prioriza el orden, nunca excluye resultados").

Confirmado por el test de regresión `productsWithoutTargetAudienceAreIncorrectlyExcluded()` en `ProductRepositoryTest`. **Recomendado priorizar la corrección** (agregar `LEFT JOIN p.targetAudience ta` explícito) ya que afecta la visibilidad real de productos en búsqueda para consumidores.

---

## `PurchaseItemRepositoryTest` — cubre `PurchaseItemRepository` — 25 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsClaimedItemsWithoutReviewOrderedByDeliveredAtDesc` | `findDeliveredItemsWithoutReview`: trae ítems CLAIMED sin review del consumer, ordenados por `deliveredAt` DESC | ✅ Pasa |
| `excludesReviewedPendingAndOtherConsumers` | Excluye ítems ya reseñados, PENDING, y de otros consumers | ✅ Pasa |
| `trueOnlyForClaimedUnreviewedOwnedItem` | `canUserReviewPurchaseItem`: true solo cuando el ítem es CLAIMED, sin review, y pertenece al consumer dado | ✅ Pasa |
| `fetchesItemWithProductAndPurchaseForOwningConsumer` | `findByIdAndConsumerId`: trae el ítem con product y purchase fetch-eados solo para el consumer dueño | ✅ Pasa |
| `countsBySnapshotCommercialIdEvenWithPurgedProduct` | `countTotalSalesByCommercialId`: cuenta por el snapshot `commercialId`, incluso si el producto ya fue purgado (product=null) | ✅ Pasa |
| `countsOnlyItemsDeliveredWithinRange` | `countTotalSalesByCommercialIdAndDatesRange`: cuenta solo los ítems entregados dentro del rango | ✅ Pasa |
| `sumsSubtotalCentsAndConvertsToPesos` | `sumTotalCommercialSalesAmountByMonth`: suma `subtotalCents` del rango y lo convierte de centavos a pesos | ✅ Pasa |
| `returnsZeroWhenNoSalesInRange` | Retorna `BigDecimal.ZERO` (no lanza NPE) cuando no hay ventas en el rango | ✅ Pasa |
| `countsSalesWithinRange` | `findTotalCommercialSalesByMonth`: cuenta las ventas del comercial dentro del rango de fechas | ✅ Pasa |
| `sumsCommissionCentsWithoutIntegerTruncation` | `sumTotalPlatformCommissionsByMonth`: regresión — suma `commissionCents` directamente sin truncar con montos no múltiplos de 100 | ✅ Pasa |
| `returnsZeroWhenNoCommissionsInRange` | Retorna `BigDecimal.ZERO` cuando no hay comisiones en el rango | ✅ Pasa |
| `countsOnlyActiveProductsOrderedByTotalSalesDesc` | `findTopSellingProducts`: solo cuenta productos ACTIVE, agrupa y ordena por total de ventas DESC | ✅ Pasa |
| `includesInactiveProductsSoldWithinRange` | `findTopSellingProductsByDateRange`: a diferencia de `findTopSellingProducts`, SÍ incluye productos inactivos si vendieron en el rango | ✅ Pasa |
| `filtersByDeliveredAtRange` | Filtra por rango de `deliveredAt` | ✅ Pasa |
| `usesSnapshotNameWhenProductPurged` | `findDailySalesByCommercialAndDateRange`: usa `COALESCE(p.name, productNameSnapshot)` cuando el producto ya fue purgado | ✅ Pasa |
| `ordersByDeliveredAtDesc` | Ordena por `deliveredAt` DESC | ✅ Pasa |
| `returnsExpiredPendingPhysicalItemsWithFullGraphFetched` | `findExpiredUnclaimedPhysicalItems`: trae ítems PENDING físicos cuyo `claimExpiresAt` ya venció, con el grafo completo fetch-eado | ✅ Pasa |
| `excludesDigitalClaimedOrNotYetExpired` | Excluye ítems digitales, ya reclamados, o cuyo plazo aún no vence | ✅ Pasa |
| `detachesProductAndStockFromAllItemsOfProduct` | `detachProductReferences`: desvincula `product` y `assignedProductStock` de todos los ítems del producto, sin borrarlos | ✅ Pasa |
| `noOptionalFiltersReturnsAllPendingPhysicalOfCommercial` | `findPendingPhysicalItems` (feature nuevo): sin filtros opcionales trae los ítems PENDING físicos del comercial | ✅ Pasa |
| `filtersByDocumentType` | Filtra por `documentType` cuando se especifica | ✅ Pasa |
| `filtersByDocumentNumberPartialCaseInsensitive` | Filtra por `documentNumber` con LIKE parcial, sin distinguir mayúsculas | ✅ Pasa |
| `returnsClaimedItemsWithoutPayoutOrOpenPqrs` | `findClaimedWithoutPayout`: trae ítems CLAIMED sin payout y sin PQRS abierto (fixtures reales cruzados de finance/pqrs) | ✅ Pasa |
| `excludesItemsAlreadyInPayout` | Excluye ítems que ya entraron a un payout | ✅ Pasa |
| `excludesItemsWithOpenPqrsButIncludesResolvedOnes` | Excluye ítems con un PQRS todavía sin resolver, pero los incluye si el PQRS ya está RESUELTA | ✅ Pasa |

## `PurchaseRepositoryTest` — cubre `PurchaseRepository` — 5 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `fetchesFullGraphForOwningConsumer` | `findByIdAndConsumerIdWithItems`: trae la compra con consumer/user/items/product/stock ya inicializados para el consumer dueño | ✅ Pasa |
| `fetchesPurchaseEvenWhenItemProductWasPurged` | Sigue trayendo la compra e ítems aunque el producto del ítem ya haya sido purgado (LEFT JOIN) | ✅ Pasa |
| `returnsEmptyWhenNotFoundOrWrongOwner` | Retorna `Optional.empty` si el `purchaseId` no existe o pertenece a otro consumer | ✅ Pasa |
| `pagesOnlyPurchasesOfGivenConsumer` | `findByConsumerId`: pagina solo las compras del consumer dado | ✅ Pasa |
| `returnsPurchaseOnlyUnderOwningConsumer` | `findByIdAndConsumerId`: retorna la compra solo bajo el consumer dueño | ✅ Pasa |

## `ProductRepositoryTest` — cubre `ProductRepository` — 22 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `countsOnlyActiveProductsOfCommercial` | `countByCommercialIdAndIsActive`: cuenta solo los productos ACTIVE del comercial dado | ✅ Pasa |
| `existsOnlyUnderOwningCommercial` | `existsByIdAndCommercialId` solo es true bajo el comercial dueño | ✅ Pasa |
| `existsByCategoryDetectsAssociatedProducts` | `existsByProductCategoryId` detecta si la categoría tiene productos asociados | ✅ Pasa |
| `findsProductOnlyUnderOwningCommercial` | `findByIdAndCommercialId` retorna el producto solo bajo el comercial dueño | ✅ Pasa |
| `returnsOnlyActiveWithCategoryFetchedRegardlessOfImage` | `findAllActiveProducts`: trae solo productos ACTIVE con categoría fetch-eada, incluso sin imagen (LEFT JOIN correcto) | ✅ Pasa |
| `noFiltersReturnsAllActive` | `searchProducts`/`searchProductsInternal` sin filtros trae todos los productos ACTIVE | ✅ Pasa |
| `filtersBySearchQueryInName` | Filtra por texto de búsqueda que coincide con el nombre, sin distinguir mayúsculas | ✅ Pasa |
| `filtersBySearchQueryInDescription` | Filtra por texto que coincide con la descripción | ✅ Pasa |
| `filtersBySearchQueryInCommercialCompanyName` | Filtra por texto que coincide con el nombre de la empresa | ✅ Pasa |
| `filtersBySearchQueryInCategoryName` | Filtra por texto que coincide con el nombre de la categoría | ✅ Pasa |
| `filtersByProductCategoryId` | Filtra por categoría exacta cuando se especifica `productCategoryId` | ✅ Pasa |
| `filtersByMinRating` | Filtra por rating mínimo (`averageRate >= minRating`) | ✅ Pasa |
| `filtersByMaxPriceCents` | Filtra por precio máximo (`priceCents <= maxPriceCents`) | ✅ Pasa |
| `municipalityPrioritizesAmongProductsWithTargetAudience` | Entre productos que SÍ tienen `TargetAudience`, el municipio prioriza el orden sin excluir al dirigido a otro municipio | ✅ Pasa |
| `productsWithoutTargetAudienceAreIncorrectlyExcluded` | **HALLAZGO (bug de producción, ver sección de arriba)**: un producto ACTIVE sin `TargetAudience` asignado (el caso más común) queda excluido de la búsqueda por un INNER JOIN implícito | ✅ Pasa (el test documenta el bug; el bug en sí sigue presente en producción) |
| `bothOverloadsReturnOnlyActiveProductsOfCommercial` | `findByCommercialId` (Page y List): ambos overloads traen solo los productos ACTIVE del comercial dado | ✅ Pasa |
| `limitsToThreeOrderedByNameDescWhileCountReturnsAll` | `findGameRewardsProducts` limita a 3 resultados ACTIVE ordenados por nombre DESC; `countGameRewards` cuenta todos | ✅ Pasa |
| `countsProductsMatchingExactStatus` | `countCommercialProducts`: cuenta los productos del comercial que coinciden exactamente con el status pedido | ✅ Pasa |
| `noFiltersReturnsAllOrderedByCreatedAtDesc` | `findAllProductsForAdmin` sin filtros trae todos los productos (cualquier status) ordenados por `createdAt` DESC | ✅ Pasa |
| `filtersByStatus` | Filtra por status cuando se especifica | ✅ Pasa |
| `filtersBySearchInNameOrCompany` | Filtra por texto de búsqueda en nombre de producto o empresa | ✅ Pasa |
| `returnsOldRejectedOrInactiveExcludingActiveAndRecent` | `findPurgeableProducts`: trae productos REJECTED/INACTIVE cuyo `updatedAt` es anterior al umbral, excluyendo ACTIVE y los recientes | ✅ Pasa |

## `ProductStockRepositoryTest` — cubre `ProductStockRepository` — 11 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsStockWithOptimisticLock` | `findByIdWithLock`: recupera el stock con lock optimista (happy path de lectura) | ✅ Pasa |
| `returnsEmptyWhenNotFound` | Retorna vacío si el id no existe | ✅ Pasa |
| `noFiltersReturnsAll` | `findByProductIdWithFilters` sin filtros trae todos los stocks del producto | ✅ Pasa |
| `filtersByStatus` | Filtra por status cuando se especifica | ✅ Pasa |
| `filtersBySoldDate` | Filtra por `soldDate` cuando se especifica | ✅ Pasa |
| `returnsEmptyWhenSoldDateDoesNotMatch` | No trae nada si `soldDate` no coincide con ningún stock | ✅ Pasa |
| `findsStockWhenMatches` | `findByIdAndProductIdAndProductCommercialId`: encuentra el stock cuando product y commercial coinciden | ✅ Pasa |
| `returnsEmptyWhenCommercialMismatch` | No encuentra el stock si el commercial no es el dueño del producto | ✅ Pasa |
| `detectsExistingCodeHash` | `existsByProductIdAndCodeHash` detecta un `codeHash` existente para el producto | ✅ Pasa |
| `returnsOnlyMatchingHashes` | `findExistingCodeHashes` retorna solo los `codeHashes` que ya existen para el producto | ✅ Pasa |
| `countsOnlyMatchingStatus` | `countByProductIdAndStatus`: cuenta solo los stocks del producto con el status pedido | ✅ Pasa |

> **Omitido**: `findNextAvailableForProduct` (native query con `FOR UPDATE SKIP LOCKED`) — H2 no soporta esa cláusula ni siquiera en `MODE=MySQL`. Requiere Testcontainers/MySQL real para probarse; queda documentado como gap conocido, no bloqueó el resto del archivo.

## `ProductReviewRepositoryTest` — cubre `ProductReviewRepository` — 7 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `averagesOnlyVisibleReviews` | `productAvgRating`: promedia solo las reviews visibles del producto | ✅ Pasa |
| `returnsNullWhenNoReviews` | Retorna null si el producto no tiene reviews | ✅ Pasa |
| `countsOnlyVisibleReviews` | `productReviewCount`: cuenta solo las reviews visibles del producto | ✅ Pasa |
| `averagesOnlyVisibleReviewsAcrossProducts` | `commercialAvgRating`: promedia solo las reviews visibles entre todos los productos del comercial | ✅ Pasa |
| `countsOnlyVisibleReviewsAcrossProducts` | `commercialReviewCount`: cuenta solo las visibles entre todos los productos del comercial | ✅ Pasa |
| `returnsAllReviewsRegardlessOfVisibility` | `getProductReviewByProductId`: trae todas las reviews del producto (visibles y ocultas), paginadas | ✅ Pasa |
| `detectsExistingReview` | `existsByConsumerIdAndProductId`: detecta si el consumer ya dejó review de ese producto | ✅ Pasa |

## `FavoriteProductRepositoryTest` — cubre `FavoriteProductRepository` — 6 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsOnlyActiveProductFavoritesOrderedByCreatedAtDesc` | `findByConsumerIdWithActiveProducts`: trae solo favoritos de productos ACTIVE, ordenados por `createdAt` DESC | ✅ Pasa |
| `returnsEmptyWhenNoActiveFavorites` | Retorna vacío si el consumer no tiene favoritos de productos activos | ✅ Pasa |
| `detectsExistingFavorite` | `existsByConsumerIdAndProductId`: detecta si el consumer ya marcó el producto como favorito | ✅ Pasa |
| `findsExactFavorite` | `findByConsumerIdAndProductId`: encuentra el favorito exacto de ese consumer y producto | ✅ Pasa |
| `returnsEmptyWhenNoFavorite` | Retorna vacío si no existe ese favorito | ✅ Pasa |
| `countsOnlyActiveProductFavorites` | `countByConsumerId`: cuenta solo los favoritos de productos ACTIVE del consumer | ✅ Pasa |

## `ProductImageAssetRepositoryTest` — cubre `ProductImageAssetRepository` — 4 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `findsAssetForProduct` | `findByProductId`: encuentra el asset asociado al producto | ✅ Pasa |
| `returnsEmptyWhenNoAsset` | Retorna vacío si el producto no tiene asset asociado | ✅ Pasa |
| `returnsOnlyMatchingStatusBeforeThreshold` | `findDeletableAssets`: trae solo los assets del status buscado con `uploadedAt` antes del threshold | ✅ Pasa |
| `returnsEmptyWhenNoMatch` | Retorna lista vacía si nada coincide | ✅ Pasa |

## `ProductCategoryImageAssetRepositoryTest` — cubre `ProductCategoryImageAssetRepository` — 2 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsOnlyMatchingStatusBeforeThreshold` | `findDeletableAssets`: trae solo los assets del status buscado con `uploadedAt` antes del threshold | ✅ Pasa |
| `returnsEmptyWhenNoMatch` | Retorna lista vacía si nada coincide | ✅ Pasa |

## `AllyProductPromotionRepositoryTest` — cubre `AllyProductPromotionRepository` — 8 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `findsExactPromotion` | `findByPremiumCommercial_IdAndProduct_Id`: encuentra la promoción exacta de ese premium y ese producto | ✅ Pasa |
| `returnsEmptyWhenNoMatch` | Retorna vacío si no existe esa combinación premium/producto | ✅ Pasa |
| `countsPromotionsOfPremium` | `countByPremiumCommercial_Id`: cuenta las promociones de ese premium | ✅ Pasa |
| `returnsPromotionsOrderedByCreatedAtDescWithProductFetched` | `findByPremiumCommercialIdOrderByCreatedAtDesc`: trae las promociones ordenadas por `createdAt` DESC, con el producto cargado | ✅ Pasa |
| `returnsOnlyActivePromotedProducts` | `findPromotedActiveProducts`: trae solo los productos promocionados con status ACTIVE | ✅ Pasa |
| `returnsDistinctAllies` | `findDistinctAlliesOfPremium`: trae los comerciales aliados distintos, sin duplicar cuando promociona varios productos del mismo aliado | ✅ Pasa |
| `returnsDistinctPromoters` | `findDistinctPromotersOfCommercial`: trae los comerciales premium distintos que promocionan productos de ese comercial | ✅ Pasa |
| `throwsOnDuplicateCombination` | Restricción única `(premiumCommercial, product)`: lanza `DataIntegrityViolationException` al persistir dos promociones con la misma combinación | ✅ Pasa |

---

## Resumen

- **Clases de test**: 9 (todas nuevas — primera cobertura de repositorio del dominio marketplace).
- **Pruebas individuales de esta categoría**: 90.
- **Total del dominio marketplace (unitarias + integración + repositorio) de esta ronda**: 188 pruebas, 0 fallos, 0 errores (`mvn test`, 2026-08-26).
- **Gap conocido documentado**: `ProductStockRepository.findNextAvailableForProduct` (nativo con `FOR UPDATE SKIP LOCKED`) no se puede probar con H2, requiere Testcontainers/MySQL real.
- **Bug de producción encontrado**: `ProductRepository.searchProductsInternal` excluye productos sin `TargetAudience` — ver sección al inicio de este documento.
