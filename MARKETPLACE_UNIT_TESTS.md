# Pruebas unitarias — Dominio Marketplace

Inventario completo de las pruebas unitarias nuevas del dominio `marketplace` (JUnit5 + Mockito/AssertJ, sin Spring context). Antes de este trabajo `ProductMapper`, `PurchaseMapper` y `AllyPromotionServiceImpl` no tenían ningún test. Los controllers/services de marketplace que ya tenían test unitario (`ProductServiceImplTest`, `PurchaseControllerTest`, etc. — ver auditoría original) no se tocaron en esta ronda; el foco fue cerrar los gaps identificados. Resultado confirmado corriendo `mvn test` el 2026-08-26 junto con el resto del lote de marketplace: **188 pruebas totales del dominio (unit+integración+repositorio), 0 fallos, 0 errores, BUILD SUCCESS**.

Todas las clases de este documento son **Nuevas**.

---

## `ProductMapperTest` — cubre `ProductMapper` — 11 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `mapsManagedFields_leavesServiceManagedFieldsUnset` | `toProduct`: mapea nombre/descripción/tipo/stock/precio (convertido a centavos); el resto queda en null/default | ✅ Pasa |
| `statusIsAlwaysAvailable` | `toProductStock`: el status queda siempre en AVAILABLE (constante del mapper) | ✅ Pasa |
| `updatesFieldsButNeverTouchesProductType` | `updateProductFromRequest`: actualiza name/description/price; NUNCA toca `productType` | ✅ Pasa |
| `mapsRelationsAndRecalculatesStock` | `toProductResponseDTO`: mapea categoryName/companyName/price y recalcula stock real vía `@AfterMapping` | ✅ Pasa |
| `imageUrlIsNeverFilled` | `imageUrl` queda `null`: el mapper no lo llena en este método (hallazgo documentado, no corregido) | ✅ Pasa |
| `zeroMaxKeysPct_meansNoKeysAllowedAndFullCash` | `maxKeysPct=0`: `maxKeysAllowed=0` y `minCashCents=priceCents` completo | ✅ Pasa |
| `typicalMaxKeysPct_calculatesProportionalSplit` | `maxKeysPct=20` (plan BASIC): calcula llaves y efectivo mínimo proporcionalmente | ✅ Pasa |
| `imageUrlIsNull_stockViaAvailableStock` | `toProductSummaryResponseDTO(Product)`: `imageUrl` queda null (mismo hallazgo); `stock` vía `getAvailableStock()` | ✅ Pasa |
| `imageUrlIsFilledFromProduct` | `toProductSummaryResponseDTO(FavoriteProduct)`: aquí SÍ se llena `imageUrl`, desde `favoriteProduct.getProduct().getImageUrl()` | ✅ Pasa |
| `nullProductInsideFavorite_throwsNpe` | `favoriteProduct.getProduct() == null`: lanza NPE (comportamiento actual, no se corrige — en producción no ocurre) | ✅ Pasa |
| `mapsEditInfoFields` | `toProductEditInfoDTO`: mapea `productCategoryId`, `price` reconvertido, `availableStockItems` y delega `targeting` a `TargetAudienceMapper` | ✅ Pasa |

## `PurchaseMapperTest` — cubre `PurchaseMapper` — 10 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `normalCase_mapsFromProduct` | `toPurchaseItemResponseDTO`: caso normal, `productId`/`imageUrl`/`productName` vienen del producto | ✅ Pasa |
| `purgedProduct_fallsBackToSnapshot` | Producto purgado (`product=null`): `productName` cae al snapshot, `productId`/`imageUrl` quedan null sin NPE | ✅ Pasa |
| `purgedProduct_canBeReviewedFalseWithoutCallingService` | `toConsumerPurchaseItemResponseDTO` con producto purgado: `canBeReviewed=false` directo, SIN llamar al service | ✅ Pasa |
| `withProduct_delegatesToProductReviewService` | Con producto presente: `canBeReviewed` delega en `productReviewService.canBeReviewed(productId, consumerId)` | ✅ Pasa |
| `purchaseResponseDTO_totalItemsAndItems` | `toPurchaseResponseDTO`: `totalItems` (Integer) = tamaño de items, mapea cada item | ✅ Pasa |
| `consumerPurchaseResponseDTO_totalItemsAndItems` | `toConsumerPurchaseResponseDTO`: `totalItems` (int primitivo) = tamaño de items, mapea cada item | ✅ Pasa |
| `mapsDirectlyFromProductAndBuyer` | `toCommercialPendingClaimResponseDTO`: mapea `productId`/`productName`/`imageUrl` DIRECTO desde `product` (sin fallback de snapshot) y datos del comprador | ✅ Pasa |
| `nullProduct_productNameStaysNull_noSnapshotFallback` | A diferencia de los otros métodos, si `product` fuera null `productName` quedaría null (sin fallback al snapshot) — hallazgo documentado, no corregido | ✅ Pasa |
| `returnsExactListSize` | `getTotalItems`: 0/1/N items retorna el tamaño exacto de la lista | ✅ Pasa |

## `AllyPromotionServiceImplTest` — cubre `AllyPromotionServiceImpl` — 13 pruebas

> Nota: `toggleAllyPromotion` tiene `@RequirePlanCapability(...)` (aspecto AOP) que nunca se ejecuta en un test Mockito puro — este test asume que el guard ya pasó y valida solo la lógica interna del método.

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `productNotFound_throwsEntityNotFoundException` | Producto inexistente: lanza `EntityNotFoundException` | ✅ Pasa |
| `existingPromotion_deletesAndShortCircuits` | La promoción ya existe: se borra y retorna sin validar elegibilidad/estado/límite (delete-first short-circuit, hallazgo documentado) | ✅ Pasa |
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

## Hallazgos documentados (no corregidos, por decisión del equipo)

1. `ProductMapper.toProductResponseDTO`/`toProductSummaryResponseDTO(Product)`: `imageUrl` queda sin asignar (`null`) — nadie lo llena en el mapper para estos dos métodos (sí se llena en el overload `toProductSummaryResponseDTO(FavoriteProduct)`).
2. `ProductMapper.toProductSummaryResponseDTO(FavoriteProduct)`: si `favoriteProduct.getProduct() == null` explota con NPE a nivel de mapper aislado (en producción no ocurre porque el repo solo trae favoritos con producto activo vía `JOIN FETCH`).
3. `PurchaseMapper.toCommercialPendingClaimResponseDTO`: a diferencia de `toPurchaseItemResponseDTO`/`toConsumerPurchaseItemResponseDTO`, no usa el fallback `resolveProductName` — si `product == null`, `productName` queda `null` en vez de usar el snapshot (en producción no ocurre porque `findPendingPhysicalItems` hace `JOIN FETCH` no-LEFT).
4. `AllyPromotionServiceImpl.toggleAllyPromotion`: si la promoción ya existe, se borra y retorna sin validar elegibilidad/estado/límite ("delete-first short-circuit").

## Resumen

- **Clases de test**: 3 (todas nuevas).
- **Pruebas individuales de esta categoría**: 33.
- **Total del dominio marketplace (unitarias + integración + repositorio) de esta ronda**: 188 pruebas, 0 fallos, 0 errores (`mvn test`, 2026-08-26).
