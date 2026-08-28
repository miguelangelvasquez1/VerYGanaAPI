# Pruebas de integración — Dominio Marketplace

Inventario completo de las pruebas de integración nuevas del dominio `marketplace`: `@WebMvcTest` con `SecurityConfig` real + JWT firmado contra `MockMvc`. Antes de este trabajo **ningún** controller de marketplace tenía test de integración (todos eran solo unitarios con Mockito) — esta ronda cubre los 6. Resultado confirmado corriendo `mvn test` el 2026-08-26 junto con el resto del lote de marketplace: **188 pruebas totales del dominio, 0 fallos, 0 errores, BUILD SUCCESS**.

Todas las clases de este documento son **Nuevas**.

---

## `ProductControllerSecurityIntegrationTest` — cubre `ProductController` — 16 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `confirmProductCreation_withoutToken_isDenied` | `POST /products/confirm` sin token se deniega y no llega al service | ✅ Pasa |
| `confirmProductCreation_withWrongRole_isForbidden` | Con rol distinto a COMMERCIAL se deniega (403) | ✅ Pasa |
| `confirmProductCreation_asCommercial_respondsOk` | Con COMMERCIAL autenticado responde 200 | ✅ Pasa |
| `deleteProduct_withoutToken_isDenied` | `DELETE /products/{id}` sin token se deniega | ✅ Pasa |
| `deleteProduct_asCommercial_respondsNoContent` | Con COMMERCIAL autenticado responde 204 | ✅ Pasa |
| `getMyProducts_withoutToken_isDenied` | `GET /products/my-products` sin token se deniega | ✅ Pasa |
| `getMyProducts_withWrongRole_isForbidden` | Con rol distinto a COMMERCIAL se deniega (403) | ✅ Pasa |
| `getMyProducts_asCommercial_respondsOk` | Con COMMERCIAL autenticado responde 200 | ✅ Pasa |
| `getFavorites_withoutToken_isDenied` | `GET /products/favorites` sin token se deniega | ✅ Pasa |
| `getFavorites_withWrongRole_isForbidden` | Con rol distinto a CONSUMER se deniega (403) | ✅ Pasa |
| `getFavorites_asConsumer_respondsOk` | Con CONSUMER autenticado responde 200 | ✅ Pasa |
| `getPrivateProductImage_withoutToken_isDenied` | `GET /products/{id}/private-image` sin token se deniega | ✅ Pasa |
| `getPrivateProductImage_withWrongRole_isForbidden` | Con CONSUMER se deniega (403) | ✅ Pasa |
| `getPrivateProductImage_asAdmin_respondsOk` | Con ADMIN responde 200 sin validar dueño | ✅ Pasa |
| `getPrivateProductImage_asCommercial_respondsOk` | Con COMMERCIAL dueño responde 200 | ✅ Pasa |
| `getProductDetail_withoutToken_respondsOk` | `GET /products/{id}` es público: sin token responde 200 | ✅ Pasa |

## `ProductCategoryControllerSecurityIntegrationTest` — cubre `ProductCategoryController` — 2 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getActiveProductCategories_withoutToken_isDenied` | `GET /productCategories` sin token se deniega y no llega al service | ✅ Pasa |
| `getActiveProductCategories_withAnyAuthenticatedRole_respondsOk` | Con cualquier rol autenticado responde 200 (endpoint sin `@PreAuthorize`, cae en la regla por defecto `anyRequest().authenticated()`) | ✅ Pasa |

## `ProductReviewControllerSecurityIntegrationTest` — cubre `ProductReviewController` — 11 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getCommercialAvgRating_withoutToken_isDenied` | `GET /productsReviews/commercial/avg` sin token se deniega | ✅ Pasa |
| `getCommercialAvgRating_withWrongRole_isForbidden` | Con rol distinto a COMMERCIAL se deniega (403) | ✅ Pasa |
| `getCommercialAvgRating_asCommercial_respondsOk` | Con COMMERCIAL autenticado responde 200 | ✅ Pasa |
| `createProductReview_withoutToken_isDenied` | `POST /productsReviews/create` sin token se deniega | ✅ Pasa |
| `createProductReview_withWrongRole_isForbidden` | Con rol distinto a CONSUMER se deniega (403) | ✅ Pasa |
| `createProductReview_asConsumer_respondsOk` | Con CONSUMER autenticado responde 200 | ✅ Pasa |
| `getProductReviewsByProductId_withoutToken_isDenied` | `GET /productsReviews/{id}` sin token se deniega | ✅ Pasa |
| `getProductReviewsByProductId_withAnyAuthenticatedRole_respondsOk` | Con cualquier rol autenticado responde 200 (sin `@PreAuthorize` específico) | ✅ Pasa |
| `hideProductReview_withoutToken_isDenied` | `PATCH /productsReviews/{id}/hide` sin token se deniega | ✅ Pasa |
| `hideProductReview_withWrongRole_isForbidden` | Con rol distinto a ADMIN se deniega (403) | ✅ Pasa |
| `hideProductReview_asAdmin_respondsNoContent` | Con ADMIN autenticado responde 204 | ✅ Pasa |

## `PurchaseControllerSecurityIntegrationTest` — cubre `PurchaseController` — 7 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `createPurchase_withoutToken_isDenied` | `POST /purchases/buy` sin token se deniega | ✅ Pasa |
| `createPurchase_withWrongRole_isForbidden` | Con rol distinto a CONSUMER se deniega (403) | ✅ Pasa |
| `createPurchase_asConsumer_respondsOk` | Con CONSUMER autenticado responde 200 | ✅ Pasa |
| `getPurchaseById_withoutToken_isDenied` | `GET /purchases/{id}` sin token se deniega | ✅ Pasa |
| `getPurchaseById_asConsumer_respondsOk` | Con CONSUMER autenticado responde 200 | ✅ Pasa |
| `getPurchases_withoutToken_isDenied` | `GET /purchases` sin token se deniega | ✅ Pasa |
| `getPurchases_asConsumer_respondsOk` | Con CONSUMER autenticado responde 200 | ✅ Pasa |

## `PurchaseItemControllerSecurityIntegrationTest` — cubre `PurchaseItemController` — 19 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getTotalCommercialSales_withoutToken_isDenied` | `GET /purchaseItems/totalSales` sin token se deniega | ✅ Pasa |
| `getTotalCommercialSales_withWrongRole_isForbidden` | Con rol distinto a COMMERCIAL se deniega (403) | ✅ Pasa |
| `getTotalCommercialSales_asCommercial_respondsOk` | Con COMMERCIAL autenticado responde 200 | ✅ Pasa |
| `getTopSellingProductsPage_asCommercial_respondsOk` | `GET /purchaseItems/topSelling` con COMMERCIAL autenticado responde 200 | ✅ Pasa |
| `getDeliveredCode_withoutToken_isDenied` | `GET /purchaseItems/{id}/delivered-code` sin token se deniega | ✅ Pasa |
| `getDeliveredCode_withWrongRole_isForbidden` | Con rol distinto a CONSUMER se deniega (403) | ✅ Pasa |
| `getDeliveredCode_asConsumer_respondsOk` | Con CONSUMER autenticado responde 200 | ✅ Pasa |
| `claimPhysicalItem_withoutToken_isDenied` | `POST /purchaseItems/{id}/claim` sin token se deniega | ✅ Pasa |
| `claimPhysicalItem_withWrongRole_isForbidden` | Con rol distinto a COMMERCIAL se deniega (403) | ✅ Pasa |
| `claimPhysicalItem_asCommercial_respondsNoContent` | Con COMMERCIAL autenticado responde 204 | ✅ Pasa |
| `getPendingClaims_withoutToken_isDenied` | `GET /purchaseItems/pending-claims` sin token se deniega (endpoint nuevo) | ✅ Pasa |
| `getPendingClaims_withWrongRole_isForbidden` | Con rol distinto a COMMERCIAL se deniega (403) | ✅ Pasa |
| `getPendingClaims_withoutQueryParams_asCommercial_respondsOk` | Sin query params, con COMMERCIAL, responde 200 | ✅ Pasa |
| `getPendingClaims_withQueryParams_asCommercial_respondsOk` | Con `documentType`/`documentNumber`, con COMMERCIAL, responde 200 | ✅ Pasa |
| `reportIssue_withoutToken_isDenied` | `POST /purchaseItems/{id}/report` sin token se deniega | ✅ Pasa |
| `reportIssue_withWrongRole_isForbidden` | Con rol distinto a CONSUMER se deniega (403) | ✅ Pasa |
| `reportIssue_asConsumer_respondsCreated` | Con CONSUMER autenticado responde 201 | ✅ Pasa |
| `submitCashRefundBankDetails_withoutToken_isDenied` | `POST /purchaseItems/{id}/cash-refund/bank-details` sin token se deniega | ✅ Pasa |
| `submitCashRefundBankDetails_asConsumer_respondsNoContent` | Con CONSUMER autenticado responde 204 | ✅ Pasa |

## `AllyPromotionControllerSecurityIntegrationTest` — cubre `AllyPromotionController` — 10 pruebas

`@PreAuthorize("hasRole('COMMERCIAL')")` a nivel de clase, verificado en los 4 endpoints.

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `togglePromotion_withoutToken_isDenied` | `PATCH /commercial/allies/promotions/{id}` sin token se deniega | ✅ Pasa |
| `togglePromotion_withWrongRole_isForbidden` | Con rol distinto a COMMERCIAL se deniega (403) | ✅ Pasa |
| `togglePromotion_asCommercial_respondsNoContent` | Con COMMERCIAL autenticado responde 204 | ✅ Pasa |
| `getMyPromotions_withoutToken_isDenied` | `GET /commercial/allies/promotions` sin token se deniega | ✅ Pasa |
| `getMyPromotions_asCommercial_respondsOk` | Con COMMERCIAL autenticado responde 200 | ✅ Pasa |
| `getMyAllies_withoutToken_isDenied` | `GET /commercial/allies` sin token se deniega | ✅ Pasa |
| `getMyAllies_withWrongRole_isForbidden` | Con rol distinto a COMMERCIAL se deniega (403) | ✅ Pasa |
| `getMyAllies_asCommercial_respondsOk` | Con COMMERCIAL autenticado responde 200 | ✅ Pasa |
| `getMyPromoters_withoutToken_isDenied` | `GET /commercial/allies/promoters` sin token se deniega | ✅ Pasa |
| `getMyPromoters_asCommercial_respondsOk` | Con COMMERCIAL autenticado responde 200 | ✅ Pasa |

---

## Resumen

- **Clases de test**: 6 (todas nuevas — primera cobertura de integración del dominio marketplace).
- **Pruebas individuales de esta categoría**: 65.
- **Total del dominio marketplace (unitarias + integración + repositorio) de esta ronda**: 188 pruebas, 0 fallos, 0 errores (`mvn test`, 2026-08-26).
