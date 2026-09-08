# Pruebas de repositorio — Dominio Finance

Inventario completo de las pruebas de repositorio nuevas del dominio `finance` (`@DataJpaTest`, H2 en memoria modo MySQL). Antes de este trabajo no existía ningún test de repositorio dedicado en el dominio. Cubre los 17 repositorios de `repositories/finance/` + 2 relacionados indispensables que viven fuera de ese árbol (`WalletRepository`, `PlanChangeRequestRepository`). Resultado confirmado corriendo `mvn test` el 2026-08-27 junto con el resto del lote de finance: **395 pruebas totales del dominio, 0 fallos, 0 errores, 2 skipped, BUILD SUCCESS**.

Todas las clases de este documento son **Nuevas**.

---

## ⚠️ Gap documentado: limitación de H2 (no un bug de producción)

`KeyTransactionRepository.findPetDailySalesByCommercial` (query nativa) usa `DATE(t.created_at) AS day` — el alias `day` choca con una palabra reservada del parser SQL de H2 (colisiona con la función `DAY()`/tipo de intervalo `DAY`), produciendo `Syntax error ... expected identifier`. Funciona correctamente contra MySQL real; es una limitación del motor de test, no del repositorio. **2 pruebas quedaron `@Disabled`** con esta explicación documentada en la clase — ver tabla de `KeyTransactionRepositoryTest` abajo.

---

## `PayoutRepository` — `PayoutRepositoryTest` — 6 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsNullWhenNoRowsInRange` | `sumTotalByCommercialIdAndPeriod`: retorna null (SUM sobre vacío, sin COALESCE) sin payouts pagados | ✅ Pasa |
| `sumsNetAmountOfPaidPayoutsInRange` | Suma `netAmountCents` de los pagados en `[startDate,endDate)` | ✅ Pasa |
| `returnsAllPayoutsInRangeOrderedDesc` | `findByScheduledAtBetweenOrderByScheduledAtDesc`: todos en el rango, orden desc, sin restringir por comercial | ✅ Pasa |
| `returnsOnlyPayoutsInGivenStatus` | `findByStatus`: solo el status indicado | ✅ Pasa |
| `findsLinkedPayoutAndEmptyWhenNotLinked` | `findByWompiTransactionId`: encuentra el vinculado, vacío si no hay ninguno | ✅ Pasa |
| `paginatesPayoutsOfCommercialInPeriodOrderedDesc` | `findByCommercialIdAndPeriod`: pagina, orden desc por `scheduledAt` | ✅ Pasa |

## `PayoutItemRepository` — `PayoutItemRepositoryTest` — 3 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `trueWhenPurchaseItemAlreadyHasPayoutItem` | `existsByPurchaseItemId`: true cuando ya entró a un payout | ✅ Pasa |
| `falseWhenPurchaseItemHasNoPayoutItem` | false sin payout item asociado | ✅ Pasa |
| `duplicatePurchaseItemThrowsDataIntegrityViolationException` | Constraint UNIQUE en `purchase_item_id`: segundo insert lanza excepción | ✅ Pasa |

## `PayoutMethodRepository` — `PayoutMethodRepositoryTest` — 7 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsOnlyMethodsOfGivenCommercial` | `findByCommercialId`: solo los métodos del commercial indicado | ✅ Pasa |
| `returnsEmptyPageWhenCommercialHasNoMethods` | Página vacía sin métodos | ✅ Pasa |
| `onlyReturnsMethodWhenOwnedByCommercial` | `findByIdAndCommercialId`: solo si pertenece al commercial (ownership) | ✅ Pasa |
| `returnsOnlyMethodsWithGivenStatus` | `findByVerificationStatus`: solo el status indicado | ✅ Pasa |
| `ignoresInactiveOrUnverifiedMethods` | `findFirstByCommercialIdAndVerificationStatusAndActiveTrue`: ignora inactivos/no-VERIFIED | ✅ Pasa |
| `returnsFirstWhenSeveralVerifiedAndActive` | Con varios VERIFIED+activos, retorna uno | ✅ Pasa |
| `emptyWhenNoVerifiedAndActiveMethods` | Vacío sin métodos VERIFIED+activos | ✅ Pasa |

## `PayoutMethodCertificateAssetRepository` — `PayoutMethodCertificateAssetRepositoryTest` — 2 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsCertificateAssetForPayoutMethod` | `findByPayoutMethodId`: trae la certificación asociada | ✅ Pasa |
| `emptyWhenPayoutMethodHasNoCertificate` | Vacío sin certificación | ✅ Pasa |

## `CopaymentRepository` — `CopaymentRepositoryTest` — 5 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `fetchesFullGraphWithoutDuplicatingRootDueToDistinct` | `findByPurchaseReferenceIdWithDetails`: cadena completa fetch-eada, sin duplicar filas por el fan-out de items (`DISTINCT`) | ✅ Pasa |
| `returnsEmptyForUnknownReference` | Vacío para `referenceId` inexistente | ✅ Pasa |
| `returnsPendingCopaymentsBeforeThresholdWithUserFetched` | `findExpiredPending`: copagos PENDING antes del umbral, con `consumer.user` fetch-eado (doble salto) | ✅ Pasa |
| `excludesOtherStatusAndNotYetExpired` | Excluye otro status y los que aún no cruzan el umbral | ✅ Pasa |
| `findsCopaymentOfPurchaseAndEmptyOtherwise` | `findByPurchaseId`: encuentra el de la compra, vacío si no hay | ✅ Pasa |

## `PurchaseItemCashRefundRepository` — `PurchaseItemCashRefundRepositoryTest` — 6 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsCashRefundForPurchaseItem` | `findByPurchaseItemId`: trae el reembolso asociado | ✅ Pasa |
| `emptyWhenPurchaseItemHasNoCashRefund` | Vacío sin reembolso | ✅ Pasa |
| `noFiltersReturnsAll` | `findByStatusAndRangeDates` sin filtros: trae todos | ✅ Pasa |
| `onlyStatusFilters` | Solo status filtra por el estado indicado | ✅ Pasa |
| `onlyDateRangeFilters` | Solo rango filtra por `createdAt` en `[start,end)` | ✅ Pasa |
| `statusAndDateRangeCombined` | Ambos combinados filtran por los dos criterios | ✅ Pasa |

## `TreasuryAccountRepository` — `TreasuryAccountRepositoryTest` — 4 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `findsExactAccountByCodeAndEmptyForMissingCode` | `findByCode`/`existsByCode`: exacta por code, vacío si no existe | ✅ Pasa |
| `returnsCorrectAccountUnderPessimisticWriteLock` | `findByCodeForUpdate`: recupera la cuenta correcta bajo lock `PESSIMISTIC_WRITE` | ✅ Pasa |
| `returnsZeroWhenNoNegativeBalances` | `countNegativeBalances`: 0 sin negativos | ✅ Pasa |
| `countsOnlyAccountsWithNegativeBalance` | Cuenta solo las de `balanceCents` negativo | ✅ Pasa |

## `TreasuryMovementRepository` — `TreasuryMovementRepositoryTest` — 3 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsOnlyExactReferenceMatch` | `findByReferenceIdAndReferenceType`: coincidencia exacta en ambos | ✅ Pasa |
| `filtersMovementsByConcept` | `findByConcept`: filtra por concepto | ✅ Pasa |
| `returnsMovementsWhereAccountIsFromOrTo` | `findByAccountCode`: trae movimientos donde la cuenta es origen O destino | ✅ Pasa |

## `WompiTransactionRepository` — `WompiTransactionRepositoryTest` — 4 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsTransactionByReference` | `findByReference`: por la referencia interna enviada a Wompi | ✅ Pasa |
| `emptyWhenReferenceDoesNotExist` | Vacío sin esa referencia | ✅ Pasa |
| `returnsTransactionByWompiId` | `findByWompiId`: por el ID real de Wompi | ✅ Pasa |
| `emptyWhenWompiIdDoesNotExist` | Vacío sin ese wompiId | ✅ Pasa |

## `KeyWalletRepository` — `KeyWalletRepositoryTest` — 4 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsKeyWalletForConsumer` | `findByConsumerId`: trae el key wallet del consumer | ✅ Pasa |
| `emptyWhenConsumerHasNoKeyWallet` | Vacío sin key wallet | ✅ Pasa |
| `trueWhenConsumerHasKeyWallet` | `existsByConsumerId`: true con key wallet | ✅ Pasa |
| `falseWhenConsumerHasNoKeyWallet` | false sin key wallet | ✅ Pasa |

## `KeyTransactionRepository` — `KeyTransactionRepositoryTest` — 18 pruebas (16 pasan, 2 deshabilitadas)

El repositorio más grande del dominio: 3 queries nativas + 1 `@Modifying`.

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `noFiltersReturnsAllOrderedDesc` | `findByConsumerId` sin filtros: todas, orden `createdAt` DESC | ✅ Pasa |
| `filtersByType` | Filtra por `type` | ✅ Pasa |
| `filtersByDateRange` | Filtra por rango de fechas | ✅ Pasa |
| `excludesOtherConsumerTransactions` | No trae transacciones de otro consumer | ✅ Pasa |
| `sumEarnedReturnsNullWhenNoRows` | `sumTotalEarnedKeysCents`: null (SUM vacío) sin créditos | ✅ Pasa |
| `sumEarnedSumsCreditTypes` | Suma purchase+connectivity de tipos CREDIT_* | ✅ Pasa |
| `sumUsedReturnsNullWhenNoRows` | `sumTotalUsedKeysCents`: null sin débitos | ✅ Pasa |
| `sumUsedSumsDebitTypes` | Suma tipos DEBIT_* | ✅ Pasa |
| `sumExpiredNullThenSum` | `sumTotalExpiredKeysCents`: null sin expiraciones, suma cuando sí hay | ✅ Pasa |
| `returnsOnlyExpiredUnprocessedCreditsWithWalletFetched` | `findExpiredNotProcessed`: créditos vencidos y no procesados, `keyWallet` fetch-eado, excluye débitos/reservas/ya-procesados | ✅ Pasa |
| `bulkMarksAsProcessedConfirmedFromDb` | `markAllAsProcessed`: bulk update confirmado releyendo de BD | ✅ Pasa |
| `includesProductWithoutSalesInPeriodWithZeroCounts` | `findPetProductSalesByCommercial` (nativa): producto sin ventas aparece igual con contadores en cero (fecha en el ON, no el WHERE — comportamiento intencional) | ✅ Pasa |
| `countsRealSalesAndUniqueBuyersScopedToCommercial` | Cuenta ventas reales, compradores únicos, no mezcla otros comerciales | ✅ Pasa |
| `excludesSalesOutsideRangeWithoutDroppingProduct` | Excluye ventas fuera de rango sin hacer desaparecer el producto | ✅ Pasa |
| `groupsByDaySummingUnitsAndRevenue` | `findPetDailySalesByCommercial` (nativa): agrupa por día sumando unidades e ingresos | ⚠️ **Deshabilitado** (limitación H2, ver arriba) |
| `returnsNothingWhenNoSales` | No devuelve días sin ventas | ⚠️ **Deshabilitado** (limitación H2, ver arriba) |
| `countsOnlyBuyersWithMoreThanOnePurchase` | `countRepeatBuyers` (nativa): solo compradores con `HAVING COUNT(*)>1` | ✅ Pasa |
| `returnsZeroWhenNoRepeatBuyers` | 0 sin compradores repetidos | ✅ Pasa |

---

## `repositories/finance/plans/`

## `PlanRepository` — `PlanRepositoryTest` — 13 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `returnsActivePlanWithMatchingCode` | `findByCodeAndActiveTrue`: plan activo con el code buscado | ✅ Pasa |
| `returnsEmptyWhenPlanIsInactive` | Vacío si el plan con ese code está inactivo | ✅ Pasa |
| `returnsEmptyWhenPlanDoesNotExist` | Vacío si no existe ese code | ✅ Pasa |
| `returnsOnlyActivePlans` | `findAllByActiveTrue`: solo los activos | ✅ Pasa |
| `returnsEmptyListWhenNoActivePlans` | Lista vacía sin planes activos | ✅ Pasa |
| `includesPlanWhenAmountEqualsMin` | `findEligiblePlans`: incluye cuando `amount == minInvestmentCents` | ✅ Pasa |
| `includesPlanWhenAmountEqualsMax` | Incluye cuando `amount == maxInvestmentCents` | ✅ Pasa |
| `excludesPlanWhenAmountExceedsMax` | Excluye cuando supera el máximo | ✅ Pasa |
| `excludesPlanWhenAmountBelowMin` | Excluye cuando es menor al mínimo | ✅ Pasa |
| `premiumWithNullMaxHasNoUpperBound` | Plan PREMIUM con `maxInvestmentCents=null`: sin límite superior | ✅ Pasa |
| `alwaysExcludesBasicPlans` | Excluye siempre BASIC sin importar el monto | ✅ Pasa |
| `excludesInactivePlans` | Excluye inactivos aunque el monto encaje | ✅ Pasa |
| `ordersDescendingByMinInvestmentCents` | Ordena descendente por `minInvestmentCents` | ✅ Pasa |

## `PlanFeatureRepository` — `PlanFeatureRepositoryTest` — 3 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `bringsFeatureFromCorrectPlan` | `findByPlanCodeAndFeatureCode`: trae la `PlanFeature` del plan correcto cuando dos planes comparten `featureCode` | ✅ Pasa |
| `returnsEmptyWhenFeatureCodeNotConfiguredForPlan` | Vacío si el plan no tiene ese `featureCode` configurado | ✅ Pasa |
| `returnsEmptyWhenPlanDoesNotExist` | Vacío si el plan no existe | ✅ Pasa |

## `FeatureRepository` — `FeatureRepositoryTest` — 2 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `savesAndReadsFeature` | Guarda un feature y lo recupera con sus campos correctos | ✅ Pasa |
| `throwsOnDuplicateCode` | Constraint unique sobre `code`: duplicar lanza `DataIntegrityViolationException` | ✅ Pasa |

## `InvestmentRepository` — `InvestmentRepositoryTest` — 7 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `findsInvestmentByReference` | `findByWompiReference`: encuentra por referencia Wompi | ✅ Pasa |
| `returnsEmptyWhenReferenceDoesNotExist` | Vacío sin esa referencia | ✅ Pasa |
| `returnsOnlyConfirmedInvestmentsForWallet` | `findByWalletAndConfirmedTrue`: solo confirmados del wallet | ✅ Pasa |
| `doesNotBringConfirmedInvestmentsFromOtherWallet` | No trae confirmados de otro wallet | ✅ Pasa |
| `filtersByDateRangeOrderedDescending` | `findByWalletIdAndPeriod`: filtra por rango, orden desc por `createdAt` | ✅ Pasa |
| `sumsOnlyConfirmedInvestmentsInPeriod` | `sumConfirmedByWalletIdAndPeriod`: suma solo los confirmados en el período | ✅ Pasa |
| `returnsZeroNotNullWhenNoConfirmedInvestments` | Retorna 0 (no null) sin confirmados — usa `COALESCE`, a diferencia de las sumas de `PayoutRepository`/`KeyTransactionRepository` | ✅ Pasa |

## `SubscriptionRepository` — `SubscriptionRepositoryTest` — 13 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `findsSubscriptionByReference` | `findByWompiReference`: por referencia Wompi | ✅ Pasa |
| `returnsEmptyWhenReferenceDoesNotExist` | Vacío sin esa referencia | ✅ Pasa |
| `findsActiveSubscriptionForCommercial` | `findByCommercialAndStatus`: activa del comercial | ✅ Pasa |
| `returnsEmptyWhenNoSubscriptionInThatStatus` | Vacío sin suscripción en ese estado | ✅ Pasa |
| `bringsActiveSubscriptionsPastEndDate` | `findExpiredActive`: ACTIVE con `endDate` ya pasado | ✅ Pasa |
| `doesNotBringActiveSubscriptionsNotYetExpired` | No trae las que aún no expiraron | ✅ Pasa |
| `doesNotBringNonActiveSubscriptions` | No trae otro estado aunque `endDate` ya pasó | ✅ Pasa |
| `bringsActiveSubscriptionsExpiringInRange` | `findExpiringBetween`: ACTIVE con `endDate` dentro del rango | ✅ Pasa |
| `doesNotBringSubscriptionsOutsideRange` | No trae las de `endDate` fuera del rango | ✅ Pasa |
| `filtersByDateRangeOrderedDescending` | `findByCommercialIdAndPeriod`: filtra por rango, orden desc | ✅ Pasa |
| `bringsAbandonedPendingCheckouts` | `findAbandonedCheckouts`: PENDING_PAYMENT creados antes del umbral | ✅ Pasa |
| `doesNotBringRecentPendingCheckouts` | No trae PENDING_PAYMENT recientes | ✅ Pasa |
| `doesNotBringNonPendingSubscriptions` | No trae otro estado aunque sean viejas | ✅ Pasa |

## `BudgetTransactionRepository` — `BudgetTransactionRepositoryTest` — 4 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `bringsTransactionsMatchingWalletAndType` | `findByWalletIdAndType`: solo las que coinciden con el tipo | ✅ Pasa |
| `doesNotBringTransactionsFromOtherWallet` | No trae de otro wallet aunque coincida el tipo | ✅ Pasa |
| `sumsSpendingWithinPeriod` | `sumByWalletIdAndPeriod`: suma el gasto dentro del período | ✅ Pasa |
| `returnsZeroNotNullWhenNoTransactionsInPeriod` | Retorna 0 (no null) sin transacciones — usa `COALESCE`, igual que `InvestmentRepository` | ✅ Pasa |

---

## Repositorios relacionados fuera de `repositories/finance/`

## `WalletRepository` — `WalletRepositoryTest` — 8 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `findsWalletForCommercial` | `findByCommercialId`: encuentra el wallet del comercial | ✅ Pasa |
| `returnsEmptyWhenCommercialHasNoWallet` (findByCommercialId) | Vacío sin wallet | ✅ Pasa |
| `bringsCorrectWalletUnderPessimisticLock` | `findByCommercialIdForUpdate`: recupera el correcto bajo lock pesimista | ✅ Pasa |
| `returnsEmptyWhenCommercialHasNoWallet` (findByCommercialIdForUpdate) | Vacío sin wallet | ✅ Pasa |
| `returnsTrueWhenCommercialHasWallet` | `existsByCommercialId`: true con wallet | ✅ Pasa |
| `returnsFalseWhenCommercialHasNoWallet` | false sin wallet | ✅ Pasa |
| `bringsOnlyWalletsWithStatusInList` | `findByStatusIn`: solo los wallets cuyo status está en la lista | ✅ Pasa |
| `returnsEmptyListWhenNoWalletMatches` | Lista vacía si ninguno coincide | ✅ Pasa |

## `PlanChangeRequestRepository` — `PlanChangeRequestRepositoryTest` — 6 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `excludesTerminalStatusesAndBringsNonTerminal` | `findByCommercial_IdAndStatusNotIn`: excluye estados terminales, trae los no-terminales | ✅ Pasa |
| `doesNotBringRequestsFromOtherCommercial` | No trae solicitudes de otro comercial | ✅ Pasa |
| `bringsRequestsFromAllCommercialsWithNonExcludedStatus` | `findByStatusNotIn`: trae de todos los comerciales cuyo estado no está excluido | ✅ Pasa |
| `returnsEmptyListWhenAllRequestsAreExcluded` | Lista vacía si todas están en estados excluidos | ✅ Pasa |
| `findsRequestLinkedToContract` | `findByContract_Id`: encuentra la vinculada al contrato | ✅ Pasa |
| `returnsEmptyWhenNoRequestLinkedToContract` | Vacío sin solicitud vinculada | ✅ Pasa |

---

## Resumen

- **Clases de test**: 19 (todas nuevas — primera cobertura de repositorio de todo el dominio finance).
- **Pruebas individuales de esta categoría**: 118 (116 pasan + 2 deshabilitadas documentadas).
- **Total del dominio finance (unitarias + integración + repositorio)**: 395 pruebas, 0 fallos, 0 errores, 2 skipped — `mvn test`, 2026-08-27.
