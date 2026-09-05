# Pruebas unitarias — Dominio Marketplace

Inventario de las pruebas unitarias del dominio `marketplace` que **no** tenían cobertura
en la auditoría original (JUnit 5 + Mockito/AssertJ, sin Spring context).

- **Ronda 1 (2026-08-26):** `ProductMapperTest`, `PurchaseMapperTest`, `AllyPromotionServiceImplTest`.
- **Ronda 2 (2026-08-30):** `ProductReviewMapperTest`, `ProductCategoryMapperTest`,
  `ProductStockMapperTest`, `ProductCleanupSchedulerTest`, `PurchaseExpirySchedulerTest`,
  `FavoriteProductTest`, `AllyProductPromotionTest`, y el test de `ProductStock.markAsInvalid()`.
  También se **resolvió el hallazgo #1** (ver sección Hallazgos).

Los controllers/services de marketplace que ya tenían test unitario (`ProductServiceImplTest`,
`PurchaseControllerTest`, etc. — ver auditoría original) no se re-documentan aquí; solo se
tocó `ProductServiceImplTest` para reflejar el fix del hallazgo #1.

Resultado confirmado con `mvn test` el 2026-08-30 sobre las clases nuevas + tocadas:
**68 pruebas, 0 fallos, 0 errores, BUILD SUCCESS**
(`ProductReviewMapperTest, ProductCategoryMapperTest, ProductStockMapperTest,
ProductCleanupSchedulerTest, PurchaseExpirySchedulerTest, FavoriteProductTest,
AllyProductPromotionTest, ProductStockTest, ProductMapperTest, ProductServiceImplTest`).

---

## Mappers

### `ProductMapperTest` — cubre `ProductMapper` — 11 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `mapsManagedFields_leavesServiceManagedFieldsUnset` | `toProduct`: mapea nombre/descripción/tipo/stock/precio (convertido a centavos); el resto queda en null/default | ✅ Pasa |
| `statusIsAlwaysAvailable` | `toProductStock`: el status queda siempre en AVAILABLE (constante del mapper) | ✅ Pasa |
| `updatesFieldsButNeverTouchesProductType` | `updateProductFromRequest`: actualiza name/description/price; NUNCA toca `productType` | ✅ Pasa |
| `mapsRelationsAndRecalculatesStock` | `toProductResponseDTO`: mapea categoryName/companyName/price y recalcula stock real vía `@AfterMapping` | ✅ Pasa |
| `imageUrlIsResolvedByService_notByMapper` | `toProductResponseDTO`: `imageUrl` queda `null` a propósito — el mapper no conoce `appBaseUrl` ni la regla PENDING/REJECTED→proxy privado; la resuelve `ProductServiceImpl.resolveImageUrl` tras el mapeo (ver hallazgo #1, resuelto) | ✅ Pasa |
| `zeroMaxKeysPct_meansNoKeysAllowedAndFullCash` | `maxKeysPct=0`: `maxKeysAllowed=0` y `minCashCents=priceCents` completo | ✅ Pasa |
| `typicalMaxKeysPct_calculatesProportionalSplit` | `maxKeysPct=20` (plan BASIC): calcula llaves y efectivo mínimo proporcionalmente | ✅ Pasa |
| `imageUrlIsNull_stockViaAvailableStock` | `toProductSummaryResponseDTO(Product)`: `imageUrl` queda null (lo resuelve el servicio); `stock` vía `getAvailableStock()` | ✅ Pasa |
| `imageUrlIsFilledFromProduct` | `toProductSummaryResponseDTO(FavoriteProduct)`: aquí SÍ se llena `imageUrl`, desde `favoriteProduct.getProduct().getImageUrl()` | ✅ Pasa |
| `nullProductInsideFavorite_throwsNpe` | `favoriteProduct.getProduct() == null`: lanza NPE (comportamiento actual, no se corrige — en producción no ocurre) | ✅ Pasa |
| `mapsEditInfoFields` | `toProductEditInfoDTO`: mapea `productCategoryId`, `price` reconvertido, `availableStockItems` y delega `targeting` a `TargetAudienceMapper` | ✅ Pasa |

### `PurchaseMapperTest` — cubre `PurchaseMapper` — 9 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `normalCase_mapsFromProduct` | `toPurchaseItemResponseDTO`: caso normal, `productId`/`imageUrl`/`productName` vienen del producto | ✅ Pasa |
| `purgedProduct_fallsBackToSnapshot` | Producto purgado (`product=null`): `productName` cae al snapshot, `productId`/`imageUrl` quedan null sin NPE | ✅ Pasa |
| `purgedProduct_canBeReviewedFalseWithoutCallingService` | `toConsumerPurchaseItemResponseDTO` con producto purgado: `canBeReviewed=false` directo, SIN llamar al service | ✅ Pasa |
| `withProduct_delegatesToProductReviewService` | Con producto presente: `canBeReviewed` delega en `productReviewService.canBeReviewed(productId, consumerId)` | ✅ Pasa |
| `purchaseResponseDTO_totalItemsAndItems` | `toPurchaseResponseDTO`: `totalItems` (Integer) = tamaño de items, mapea cada item | ✅ Pasa |
| `consumerPurchaseResponseDTO_totalItemsAndItems` | `toConsumerPurchaseResponseDTO`: `totalItems` (int primitivo) = tamaño de items, mapea cada item | ✅ Pasa |
| `mapsDirectlyFromProductAndBuyer` | `toCommercialPendingClaimResponseDTO`: mapea `productId`/`productName`/`imageUrl` DIRECTO desde `product` (sin fallback de snapshot) y datos del comprador | ✅ Pasa |
| `nullProduct_productNameStaysNull_noSnapshotFallback` | A diferencia de los otros métodos, si `product` fuera null `productName` quedaría null (sin fallback al snapshot) — hallazgo #3, documentado | ✅ Pasa |
| `returnsExactListSize` | `getTotalItems`: 0/1/N items retorna el tamaño exacto de la lista | ✅ Pasa |

### `ProductReviewMapperTest` — cubre `ProductReviewMapper` — 5 pruebas — **Nueva (ronda 2)**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `mapsOnlyCommentAndRating` | `toProductReview`: mapea solo `comment` y `rating`; `purchaseItemId` del DTO no tiene campo destino (el servicio resuelve `purchaseItem`); `id/consumer/product/createdAt` null y `visible=false` (lo pone `@PrePersist`, no el mapper) | ✅ Pasa |
| `nullRequest_returnsNull` | `toProductReview(null)` → `null` | ✅ Pasa |
| `mapsFieldsAndConsumerName` | `toProductReviewResponseDTO`: mapea `id/comment/rating/createdAt` y `consumerName` desde `consumer.name` | ✅ Pasa |
| `nullConsumer_consumerNameIsNull` | `consumer == null`: `consumerName` queda null, sin NPE | ✅ Pasa |
| `nullReview_returnsNull` | `toProductReviewResponseDTO(null)` → `null` | ✅ Pasa |

### `ProductCategoryMapperTest` — cubre `ProductCategoryMapper` — 5 pruebas — **Nueva (ronda 2)**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `mapsOnlyName` | `toProductCategory`: mapea solo el nombre; `active=false` (primitivo, lo pone `@PrePersist`), `id/createdAt/imageAsset/createdBy` null | ✅ Pasa |
| `nullRequest_returnsNull` | `toProductCategory(null)` → `null` | ✅ Pasa |
| `withoutImageAsset_imageUrlIsNull` | `toProductCategoryResponseDTO`: sin `imageAsset`, mapea `id/name` y `imageUrl` queda null | ✅ Pasa |
| `withImageAsset_imageUrlFromCdn` | con `imageAsset`: `imageUrl` se arma con la URL pública del CDN (`expression = java(getImageUrl())`) | ✅ Pasa |
| `nullCategory_returnsNull` | `toProductCategoryResponseDTO(null)` → `null` | ✅ Pasa |

### `ProductStockMapperTest` — cubre `ProductStockMapper` — 5 pruebas — **Nueva (ronda 2)**

> Ojo: este `toProductStock` usa `@Mapping(target = "status", ignore = true)`, a diferencia del `toProductStock` de `ProductMapper` (que usa `constant = "AVAILABLE"`). Aquí el AVAILABLE viene del `@Builder.Default` de `ProductStock`.

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `mapsCodeAndExpiration` | `toProductStock`: mapea `code` y `expirationDate`; `status=AVAILABLE` (default del builder) y `id/version/product/purchaseItem/soldAt/codeHash/createdAt/updatedAt` null | ✅ Pasa |
| `nullExpirationDate_isAllowed` | código que no vence (`expirationDate == null`): se mapea como null sin fallar | ✅ Pasa |
| `nullRequest_returnsNull` | `toProductStock(null)` → `null` | ✅ Pasa |
| `mapsResponseFields` | `toProductStockResponseDTO`: mapea `id/status/createdAt/soldAt` | ✅ Pasa |
| `nullStock_returnsNull` | `toProductStockResponseDTO(null)` → `null` | ✅ Pasa |

---

## Schedulers

### `PurchaseItemExpirationSchedulerTest` — cubre `PurchaseItemExpirationScheduler` — 4 pruebas *(preexistente)*

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `noExpiredItems_doesNothing` | sin ítems físicos vencidos: no toca refund ni email | ✅ Pasa |
| `expiredItem_expiresAndNotifiesBoth` | ítem vencido: lo expira y notifica a comprador y comerciante | ✅ Pasa |
| `expireFailsForOneItem_skipsNotificationsButContinuesWithOthers` | falla `expireUnclaimed` para un ítem: no lo notifica, pero sigue con los demás | ✅ Pasa |
| `consumerEmailFails_stillNotifiesCommercial` | falla el correo al comprador: igual notifica al comerciante | ✅ Pasa |

### `ProductCleanupSchedulerTest` — cubre `ProductCleanupScheduler` — 4 pruebas — **Nueva (ronda 2)**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `noCandidates_doesNothing` | sin productos REJECTED/INACTIVE elegibles: no intenta purgar nada | ✅ Pasa |
| `multipleCandidates_purgesEach` | varios candidatos: llama `purgeProduct` para cada uno | ✅ Pasa |
| `onePurgeFails_continuesWithOthers` | una purga lanza excepción: se captura y se sigue con los demás productos | ✅ Pasa |
| `usesConfiguredGracePeriod` | usa el `gracePeriodDays` inyectado por configuración (no un valor fijo) | ✅ Pasa |

### `PurchaseExpirySchedulerTest` — cubre `PurchaseExpiryScheduler` — 2 pruebas — **Nueva (ronda 2)**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `delegatesToExpireStaleWithDefaultWindow` | disparador delgado: delega en `copaymentService.expireStale(30)` y nada más | ✅ Pasa |
| `usesConfiguredWindow` | respeta el `maxAgeMinutes` inyectado por configuración | ✅ Pasa |

---

## Entidades (models)

### `ProductStockTest` — cubre `ProductStock` — 6 pruebas *(preexistente, +1 en ronda 2)*

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `markAsReserved_setsReservedStatus` | `markAsReserved`: pasa a RESERVED | ✅ Pasa |
| `markAsSold_setsSoldStatusAndPurchaseItem` | `markAsSold`: pasa a SOLD, guarda el `purchaseItem` y setea `soldAt` | ✅ Pasa |
| `markAsAvailable_clearsSaleInfo` | `markAsAvailable`: libera el stock (limpia `purchaseItem` y `soldAt`) al cancelar la compra | ✅ Pasa |
| `markAsInvalid_setsInvalidStatus` | **(nueva)** `markAsInvalid`: pasa a INVALID (código reportado); no vuelve al inventario disponible | ✅ Pasa |
| `isExpired_trueWhenPast` | `isExpired`: true cuando `expirationDate` ya pasó | ✅ Pasa |
| `isExpired_falseWhenNoExpirationOrFuture` | `isExpired`: false sin fecha o con fecha futura | ✅ Pasa |

### `FavoriteProductTest` — cubre `FavoriteProduct` — 1 prueba — **Nueva (ronda 2)**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `onCreate_setsCreatedAtInUtc` | hook `@PrePersist`: sella `createdAt` en UTC | ✅ Pasa |

### `AllyProductPromotionTest` — cubre `AllyProductPromotion` — 1 prueba — **Nueva (ronda 2)**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `onCreate_setsCreatedAtInUtc` | hook `@PrePersist`: sella `createdAt` en UTC | ✅ Pasa |

> Otras entidades del dominio (`Product`, `Purchase`, `PurchaseItem`, `ProductCategory`,
> `ProductReview`) ya tenían su `*Test` en la auditoría original.

---

## Services

### `AllyPromotionServiceImplTest` — cubre `AllyPromotionServiceImpl` — 13 pruebas

> Nota: `toggleAllyPromotion` tiene `@RequirePlanCapability(...)` (aspecto AOP) que nunca se ejecuta en un test Mockito puro — este test asume que el guard ya pasó y valida solo la lógica interna del método.

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `productNotFound_throwsEntityNotFoundException` | Producto inexistente: lanza `EntityNotFoundException` | ✅ Pasa |
| `existingPromotion_deletesAndShortCircuits` | La promoción ya existe: se borra y retorna sin validar elegibilidad/estado/límite (delete-first short-circuit, hallazgo #4) | ✅ Pasa |
| `notEligibleAlly_nullPlan_throwsInvalidRequestException` | Promoción no existe, aliado no elegible (plan null): lanza `InvalidRequestException` | ✅ Pasa |
| `notEligibleAlly_premiumPlan_throwsInvalidRequestException` | Promoción no existe, aliado no elegible (plan PREMIUM): lanza `InvalidRequestException` | ✅ Pasa |
| `eligibleAllyButProductNotActive_throwsInvalidStatusException` | Aliado elegible, producto no ACTIVE: lanza `InvalidStatusException` | ✅ Pasa |
| `maxPromotionsReached_throwsAllyPromotionException` | Aliado elegible, producto ACTIVE, pero ya hay 3 promociones: lanza `AllyPromotionException` | ✅ Pasa |
| `premiumCommercialNotFound_throwsEntityNotFoundException` | Todo válido pero el comercio premium no existe: lanza `EntityNotFoundException` | ✅ Pasa |
| `happyPath_savesNewPromotion` | Caso feliz: todo válido, guarda una `AllyProductPromotion` nueva con `premiumCommercial`/`product` seteados | ✅ Pasa |
| `withPromotions_mapsToResponseDTO` | `getMyPromotions` con promociones: mapea productId/productName/productImageUrl/allyCommercialId/allyCommercialName/priceCents/promotedAt | ✅ Pasa |
| `noPromotions_returnsEmptyList` | Sin promociones: retorna lista vacía | ✅ Pasa |
| `getMyAllies_mapsCommercialResponse` | `getMyAllies`: mapea commercialId/companyName/planCode desde `currentPlan.code` | ✅ Pasa |
| `getMyPromoters_mapsCommercialResponse` | `getMyPromoters`: mismo mapeo | ✅ Pasa |
| `nullCurrentPlan_planCodeIsNullWithoutNpe` | `commercial.currentPlan == null`: `planCode` queda null, sin NPE | ✅ Pasa |

---

## Hallazgos

### #1 — `ProductMapper` no llenaba `imageUrl` → **RESUELTO (ronda 2)**

`ProductMapper.toProductResponseDTO` / `toProductSummaryResponseDTO(Product)` dejan `imageUrl`
en `null` **a propósito**: la URL depende del status (`PENDING`/`REJECTED` → endpoint proxy
privado con `appBaseUrl`; el resto → CDN público) y el mapper no tiene esa información.
El contrato es que `ProductServiceImpl` la resuelve tras el mapeo vía
`resolveImageUrl(product)`.

El problema real era que 2 de los métodos que devuelven `ProductResponseDTO` se saltaban ese
paso: **`approveProductForAdmin` y `rejectProductForAdmin`**. Fix aplicado en
`ProductServiceImpl`:

- `approveProductForAdmin`: tras el mapeo, `response.setImageUrl(resolveImageUrl(product))`
  (producto ya ACTIVE → URL pública del CDN).
- `rejectProductForAdmin`: al borrar el `ProductImageAsset` se hace también
  `product.setImageAsset(null)` en memoria, de modo que `resolveImageUrl` devuelve `null` en
  vez de una URL apuntando a un objeto ya borrado.

Tests actualizados: `ProductMapperTest.imageUrlIsResolvedByService_notByMapper` (documenta que
es intencional del mapper) y `ProductServiceImplTest` (`ApproveProduct` / `RejectProduct` ahora
asertan la `imageUrl` de la respuesta).

### #2 — `toProductSummaryResponseDTO(FavoriteProduct)` NPE si `product == null` — se mantiene

`java(favoriteProduct.getProduct().getImageUrl())` explota con NPE a nivel de mapper aislado.
No se corrige: en producción el repositorio de favoritos solo trae favoritos con producto
activo vía `JOIN FETCH` (no `LEFT JOIN`), así que `product` nunca es null por ese camino.
Cubierto por `ProductMapperTest.nullProductInsideFavorite_throwsNpe`.

### #3 — `PurchaseMapper.toCommercialPendingClaimResponseDTO` sin fallback de snapshot — se mantiene

A diferencia de `toPurchaseItemResponseDTO` / `toConsumerPurchaseItemResponseDTO`, no usa
`resolveProductName`: si `product == null`, `productName` queda `null` en vez de usar el
snapshot. No se corrige: `findPendingPhysicalItems` hace `JOIN FETCH` no-`LEFT`, así que el
producto siempre está presente en este flujo.
Cubierto por `PurchaseMapperTest.nullProduct_productNameStaysNull_noSnapshotFallback`.

### #4 — `AllyPromotionServiceImpl.toggleAllyPromotion` delete-first short-circuit — se mantiene (comportamiento correcto)

Si la promoción ya existe, se borra y se retorna sin validar elegibilidad/estado/límite. Es el
comportamiento deseado: al **desactivar** un toggle no tiene sentido re-validar las
precondiciones de **activación**. Se documenta explícitamente para que no se confunda con un
bug. Cubierto por `AllyPromotionServiceImplTest.existingPromotion_deletesAndShortCircuits`.

---

## Resumen

| Categoría | Clases | Pruebas |
|---|---|---|
| Mappers (ronda 1) | `ProductMapperTest`, `PurchaseMapperTest` | 20 |
| Mappers (ronda 2, nuevas) | `ProductReviewMapperTest`, `ProductCategoryMapperTest`, `ProductStockMapperTest` | 15 |
| Schedulers (ronda 2, nuevas) | `ProductCleanupSchedulerTest`, `PurchaseExpirySchedulerTest` | 6 |
| Entidades (ronda 2, nuevas) | `FavoriteProductTest`, `AllyProductPromotionTest` (+`markAsInvalid` en `ProductStockTest`) | 3 |
| Services (ronda 1) | `AllyPromotionServiceImplTest` | 13 |
| **Total nuevo (rondas 1+2)** | **10 clases** | **57** |

Hallazgos: **1 resuelto**, 3 documentados con justificación (no son bugs en producción).
