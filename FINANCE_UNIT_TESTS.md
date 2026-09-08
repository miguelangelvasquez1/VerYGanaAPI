# Pruebas unitarias — Dominio Finance

Inventario completo de las pruebas unitarias nuevas del dominio `finance` (JUnit5 + Mockito/AssertJ, sin Spring context). Antes de este trabajo, de los 7 controllers reales de finance solo `PlanController` tenía test (ninguno de los otros 6 tenía ni unitario ni integración), y `PlanChangeRequestServiceImpl`, `PlanFeatureGuard`, `PlanGuardAspect`, `BudgetService`, `InvestmentService`, `EffectivePlanResolver`, los 3 mappers de finance, `WompiClient`, `WompiService` y `WompiWebhookDispatcher` no tenían ningún test. Resultado confirmado corriendo `mvn test` el 2026-08-27 junto con el resto del lote de finance: **395 pruebas totales del dominio (unit+integración+repositorio), 0 fallos, 0 errores, 2 skipped (documentadas en el doc de repositorio), BUILD SUCCESS**.

Todas las clases de este documento son **Nuevas**. `SubscriptionService` es un stub vacío sin lógica — no se testeó. `WompiPayoutClientTest` ya existía (integración Wompi payouts previa) y no forma parte de este documento.

---

## Servicios

### `PlanChangeRequestServiceImplTest` — cubre `PlanChangeRequestServiceImpl` — 32 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `commercialNotFound_throwsEntityNotFoundException` | `requestPlanChange`: comercial no encontrado | ✅ Pasa |
| `targetPlanNotFound_throwsValidationException` | Plan destino no encontrado o inactivo | ✅ Pasa |
| `samePlanAsCurrent_throwsValidationException` | Mismo plan que el actual | ✅ Pasa |
| `downgradeToBasicWithBalance_throwsValidationExceptionWithBalance` | Downgrade a BASIC con saldo > 0: excepción con el saldo en el mensaje | ✅ Pasa |
| `downgradeToBasicWithZeroBalance_continues` | Downgrade a BASIC con saldo == 0: permite continuar | ✅ Pasa |
| `duplicateRequestInProgress_throwsValidationException` | Solicitud duplicada ya en curso | ✅ Pasa |
| `openRechargeContract_throwsValidationException` | Recarga/contrato abierto | ✅ Pasa |
| `happyPath_savesTwiceAndLinksContract` | Camino feliz: guarda dos veces (REQUESTED, luego CONTRACT_PENDING_REVIEW) y vincula el contrato | ✅ Pasa |
| `computeRequiredTopUp_basic_returnsMonthlyPrice` | `computeRequiredTopUp` para BASIC retorna `monthlyPriceCents` | ✅ Pasa |
| `noSideEffects` | `previewPlanChange`: no tiene efectos secundarios (`save` nunca se llama) | ✅ Pasa |
| `downgradeToBasicEligible` | Downgrade a BASIC elegible (saldo 0): `eligible=true` y mensaje correspondiente | ✅ Pasa |
| `downgradeToBasicNotEligible` | Downgrade a BASIC no elegible (saldo > 0): `eligible=false` y mensaje con el saldo | ✅ Pasa |
| `fromPlanNull_message` | `fromPlan==null`: mensaje de aplicación inmediata tras abono | ✅ Pasa |
| `fromPlanBasic_message` | `fromPlan==BASIC`: mensaje de aplicación inmediata tras abono | ✅ Pasa |
| `requiredTopUpPositive_message` | `requiredTopUp > 0` (upgrade STANDARD/PREMIUM): mensaje de abono adicional | ✅ Pasa |
| `requiredTopUpZeroOrLess_message` | `requiredTopUp <= 0`: mensaje sin pago adicional | ✅ Pasa |
| `targetBasic_onlyMonthlyPricePopulated` | Destino BASIC: solo `targetMonthlyPriceCents` poblado, min/max en null | ✅ Pasa |
| `targetNonBasic_onlyMinMaxPopulated` | Destino no-BASIC: solo `targetMinInvestmentCents`/`targetMaxInvestmentCents` poblados | ✅ Pasa |
| `requestNotFound_throwsEntityNotFoundException` | `cancelPlanChangeRequest`: solicitud inexistente | ✅ Pasa |
| `requestBelongsToAnotherCommercial_throwsEntityNotFoundException` | Solicitud de otro comercial: mismo mensaje que "no existe" (hallazgo de aislamiento entre tenants, documentado) | ✅ Pasa |
| `wrongStatus_throwsValidationException` | Estado distinto de REQUESTED/CONTRACT_PENDING_REVIEW | ✅ Pasa |
| `happyPathFromRequested_marksCancelled` | Caso feliz desde REQUESTED: marca CANCELLED y guarda | ✅ Pasa |
| `happyPathFromContractPendingReview_marksCancelled` | Caso feliz desde CONTRACT_PENDING_REVIEW: marca CANCELLED y guarda | ✅ Pasa |
| `delegatesAndReturnsFirst` | `getCurrent`: delega en `findByCommercial_IdAndStatusNotIn` y retorna el primero | ✅ Pasa |
| `noneNonTerminal_returnsNull` | Sin ninguna no-terminal: retorna null | ✅ Pasa |
| `listPendingReview_delegates` | `listPendingReview`: delega en `findByStatusNotIn` | ✅ Pasa |
| `wrongPurpose_ignoresEvent` | `onContractSigned`: `purpose != PLAN_CHANGE` ignora el evento | ✅ Pasa |
| `planChangeWithNullTopUp_appliesImmediately` | `purpose==PLAN_CHANGE` y `topUp==null`: aplica de inmediato | ✅ Pasa |
| `planChangeWithZeroTopUp_appliesImmediately` | `topUp<=0`: aplica de inmediato | ✅ Pasa |
| `planChangeWithPositiveTopUp_movesToPaymentPending` | `topUp>0`: pasa a PAYMENT_PENDING sin aplicar | ✅ Pasa |
| `requestNotFound_isNoOp` (applyIfPending) | Solicitud inexistente: no-op | ✅ Pasa |
| `wrongStatus_isNoOp` (applyIfPending) | `status != PAYMENT_PENDING`: no-op | ✅ Pasa |
| `paymentPending_applies` | `status == PAYMENT_PENDING`: aplica correctamente | ✅ Pasa |

### `PlanFeatureGuardTest` — cubre `PlanFeatureGuard` — 13 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `canAdvertise` | `CAN_ADVERTISE`: pasa si true, lanza si false | ✅ Pasa |
| `canUseGames` | `CAN_USE_GAMES`: pasa si true, lanza si false | ✅ Pasa |
| `canUseSurveys` | `CAN_USE_SURVEYS`: pasa si true, lanza si false | ✅ Pasa |
| `canSellDirectly` | `CAN_SELL_DIRECTLY`: pasa si true, lanza si false | ✅ Pasa |
| `canHavePets` | `CAN_HAVE_PETS`: pasa si true, lanza si false | ✅ Pasa |
| `canPromoteAllyProducts` | `CAN_PROMOTE_ALLY_PRODUCTS`: pasa si true, lanza si false | ✅ Pasa |
| `canExportReport` | `CAN_EXPORT_REPORT`: pasa si true, lanza si false | ✅ Pasa |
| `maxProducts` | `MAX_PRODUCTS`: conteo < máximo pasa; conteo == máximo lanza | ✅ Pasa |
| `maxAds` | `MAX_ADS`: conteo < máximo pasa; conteo == máximo lanza | ✅ Pasa |
| `maxSurveys` | `MAX_SURVEYS`: conteo < máximo pasa; conteo == máximo lanza | ✅ Pasa |
| `maxBrandedGames` | `MAX_BRANDED_GAMES`: suma campañas no finalizadas + branding requests activos contra el máximo | ✅ Pasa |
| `budgetSuspended_throwsBudgetSuspendedException` | `assertBudgetAvailable`: `budgetSuspended==true` lanza `BudgetSuspendedException` | ✅ Pasa |
| `budgetNotSuspended_doesNotThrow` | `budgetSuspended==false`: no lanza | ✅ Pasa |

### `PlanGuardAspectTest` — cubre `PlanGuardAspect` (primer `@Aspect` testeado del proyecto) — 9 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `defaultParamName_extractsCorrectly` | Extrae `commercialId` cuando el parámetro se llama `"commercialId"` (default) | ✅ Pasa |
| `customParamName_extractsCorrectly` | Extrae `commercialId` cuando `commercialIdParam()` apunta a otro nombre | ✅ Pasa |
| `noMatchingParam_throwsIllegalArgumentException` | Ningún parámetro coincide: lanza `IllegalArgumentException` | ✅ Pasa |
| `validatesAllCapabilitiesInOrder_whenAllPass` | Valida TODAS las capacidades de `value()` en orden cuando todas pasan | ✅ Pasa |
| `firstCapabilityFails_stopsAndPropagates` | Si la primera capacidad falla, no verifica las demás ni el budget, propaga sin `proceed()` | ✅ Pasa |
| `allCapabilitiesPassAndRequiresBudget_callsAssertBudgetAvailable` | Capacidades OK y `requiresBudget()==true`: llama `assertBudgetAvailable` | ✅ Pasa |
| `requiresBudgetFalse_neverCallsAssertBudgetAvailable` | `requiresBudget()==false`: nunca llama `assertBudgetAvailable` | ✅ Pasa |
| `assertBudgetAvailableThrows_propagatesWithoutProceed` | `assertBudgetAvailable` lanza: propaga sin llamar `proceed()` | ✅ Pasa |
| `proceedInvokedAndResultReturned_onlyWhenAllValidationsPass` | `proceed()` se invoca y su resultado se retorna solo si todo pasa | ✅ Pasa |

### `BudgetServiceTest` — cubre `BudgetService` — 8 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `generatesAdViewTransaction` | `consumeForAdView`: genera `BudgetTransaction` tipo AD_VIEW con el `referenceId` dado | ✅ Pasa |
| `generatesGameRewardTransaction` | `consumeForGameReward`: tipo GAME_REWARD | ✅ Pasa |
| `generatesManualAdjustmentTransaction` | `applyManualAdjustment`: tipo MANUAL_ADJUSTMENT sin `referenceId`, con la descripción dada | ✅ Pasa |
| `generatesBrandingRequestTransaction` | `consumeForBrandingRequest`: tipo BRANDING_REQUEST | ✅ Pasa |
| `noWallet_throwsIllegalArgumentException` | Sin wallet: lanza `IllegalArgumentException` sin consumir ni persistir | ✅ Pasa |
| `usesLockedLookup` | Usa el lock pesimista `findByCommercialIdForUpdate`, no `findByCommercialId` | ✅ Pasa |
| `notEnoughBalance_propagatesExceptionWithoutPersisting` | Saldo insuficiente: propaga `InsufficientFundsException` sin persistir transacción | ✅ Pasa |
| `exhaustsWallet_notifiesInvestmentService` | El consumo agota el wallet: notifica a `InvestmentService.handleWalletExhausted` | ✅ Pasa |
| `doesNotExhaustWallet_neverNotifiesInvestmentService` | No agota: nunca notifica | ✅ Pasa |

### `InvestmentServiceTest` — cubre `InvestmentService` — 10 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `commercialNotFound_throwsValidationException` | `createInvestment`: comercial no encontrado | ✅ Pasa |
| `noCurrentPlan_throwsValidationException` | Comercial sin plan activo | ✅ Pasa |
| `noExistingWallet_createsWalletOnTheFly` | Sin wallet previo: lo crea on-the-fly antes de depositar | ✅ Pasa |
| `belowMinimumTotal_throwsValidationExceptionWithTotal` | Saldo total resultante < 1.000.000 COP: excepción con el total | ✅ Pasa |
| `nonExactCents_throwsArithmeticException` | Depósito con más de 2 decimales: `longValueExact()` lanza `ArithmeticException` (comportamiento actual, no corregido) | ✅ Pasa |
| `happyPath_depositsAndPersistsInvestment` | Camino feliz: deposita, persiste `Investment` con snapshot del plan, retorna DTO | ✅ Pasa |
| `commercialNotFound_isNoOp` (handleWalletExhausted) | Comercial inexistente: no-op silencioso | ✅ Pasa |
| `commercialFound_sendsEmailAndNotification` (handleWalletExhausted) | Comercial existente: envía email y notificación de saldo agotado | ✅ Pasa |
| `commercialNotFound_isNoOp` (handleWalletReplenished) | Comercial inexistente: no-op silencioso | ✅ Pasa |
| `commercialFound_sendsEmailAndNotification` (handleWalletReplenished) | Comercial existente: envía email y notificación de saldo restaurado | ✅ Pasa |

### `EffectivePlanResolverTest` — cubre `EffectivePlanResolver` — 25 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `commercialNotFound_throwsIllegalArgumentException` | Comercial no encontrado | ✅ Pasa |
| `noCurrentPlan_returnsNoPlanMode` | `currentPlan==null`: retorna `EffectivePlanState.noPlanMode()` | ✅ Pasa |
| `basicPlan_neverTouchesWallet` | BASIC: `balanceCents=0`, nunca consulta `WalletRepository` | ✅ Pasa |
| `standardPlan_withWallet_readsBalanceFromWallet` | STANDARD/PREMIUM con wallet: lee `balanceCents` real | ✅ Pasa |
| `standardPlan_withoutWallet_usesZero` | STANDARD/PREMIUM sin wallet: usa 0 | ✅ Pasa |
| `basic_neverSuspended` | BASIC nunca está suspendido, aunque su balance sea 0 | ✅ Pasa |
| `nonBasicZeroBalance_suspended` | STANDARD/PREMIUM con balance 0: suspendido | ✅ Pasa |
| `nonBasicPositiveBalance_notSuspended` | STANDARD/PREMIUM con balance > 0: no suspendido | ✅ Pasa |
| `withOverride_usesOverride` (booleana, x3: canAdvertise/maxProducts/visibilityBoostPct) | Con override del onboarding: usa el override e ignora `PlanFeature` | ✅ Pasa (×3) |
| `withoutOverride_fallsBackToPlanFeature` (×3) | Sin override: cae al `PlanFeature` del plan | ✅ Pasa (×3) |
| `withoutOverrideOrPlanFeature_usesHardcodedDefault` (×3) | Sin override y sin `PlanFeature`: cae al default hardcodeado | ✅ Pasa (×3) |
| `planFeaturePresent_usesItsValue` | `canExportReport` (sin override disponible): `PlanFeature` presente usa su valor | ✅ Pasa |
| `planFeatureAbsent_usesHardcodedDefault` | `PlanFeature` ausente: cae al default hardcodeado (false) | ✅ Pasa |
| `noPlan_returnsZeroZero` | `resolveBudgetThresholds` sin plan: retorna (0,0) | ✅ Pasa |
| `basicPlan_returnsZeroZero` | Plan BASIC: retorna (0,0) | ✅ Pasa |
| `nonBasicWithFixedCents_prioritizesFixedOverPct` | Plan no-BASIC: prioriza el monto fijo sobre el porcentaje | ✅ Pasa |
| `nonBasicWithoutFixedCents_usesConfiguredPct` | Sin monto fijo: usa el porcentaje configurado | ✅ Pasa |
| `nonBasicWithoutFixedOrConfiguredPct_fallsBackToWalletPct` | Sin monto fijo ni porcentaje: usa el 10% plano de `Wallet.lowBalanceThresholdPct` | ✅ Pasa |
| `criticalCents_onlyComputedWhenPctPositive` | `criticalCents` solo se calcula cuando `LOW_BALANCE_CRITICAL_PCT > 0` | ✅ Pasa |

---

## Mappers

### `MoneyMapperTest` — cubre `MoneyMapper` — 10 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `typicalPositiveValue` | `toCents`: `100.00 → 10000L` | ✅ Pasa |
| `zero` (toCents) | `0 → 0L` | ✅ Pasa |
| `negativeValue` (toCents) | `-50.25 → -5025L` | ✅ Pasa |
| `extraDecimals_areTruncatedNotRounded` | Decimales con más de 2 dígitos: TRUNCA, no redondea (hallazgo documentado) | ✅ Pasa |
| `nullAmount_throwsNPE` | `null`: lanza NPE (comportamiento actual, no corregido) | ✅ Pasa |
| `typicalValue` (fromCents) | `10000L → 100.00` | ✅ Pasa |
| `zero` (fromCents) | `0L → 0` | ✅ Pasa |
| `negativeValue` (fromCents) | `-5025L → -50.25` | ✅ Pasa |
| `nullCents_throwsNPE` | `null`: NPE (unboxing de `Long` null) | ✅ Pasa |
| `quotientAlwaysExact_neverThrowsArithmeticException` | Cociente nunca es inexacto en base 10 con divisor 100 — nunca lanza `ArithmeticException` | ✅ Pasa |

### `KeyTransactionMapperTest` — cubre `KeyTransactionMapper` — 9 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `exactDelta_dividesEvenly` | 5000 cents con `keyValueCents=1000` → 5 keys | ✅ Pasa |
| `inexactDelta_truncates` | 4999 cents → trunca a 4, no redondea | ✅ Pasa |
| `nullDelta_returnsNull` | Delta null → null | ✅ Pasa |
| `negativeDelta_preservesSign` | Delta negativo (débito): el signo se preserva | ✅ Pasa |
| `negativeInexactDelta_truncatesTowardZero` | -4999/1000 trunca hacia cero (-4), no hacia -5 | ✅ Pasa |
| `differentKeyValueCents_changesCalculationProportionally` | `keyValueCents` distinto del default (500): cálculo cambia proporcionalmente | ✅ Pasa |
| `mapsBothDeltasThroughCentsToKeysDelta` | Mapea `purchaseKeysDeltaCents→purchaseKeysDelta` y `connectivityKeysDeltaCents→connectivityKeysDelta` | ✅ Pasa |
| `nullDeltaInEntity_mapsToNullInDto` | Delta null en la entidad se mapea a null en el DTO | ✅ Pasa |
| `nullEntity_returnsNull` | Entidad null → DTO null | ✅ Pasa |

### `PayoutMethodMapperTest` — cubre `PayoutMethodMapper` — 5 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `copiesNonIgnoredFields` | Copia los campos NO ignorados: type, alias, bankCode, accountNumber, bankAccountType, etc. | ✅ Pasa |
| `ignoredFieldsWithoutEntityDefault_areNull` | `id`, `commercial`, `rejectionReason`, `createdAt`, `verifiedAt`, `certificateAsset` quedan null | ✅ Pasa |
| `ignoredFieldsWithEntityDefault_useBuilderDefaults` | `verificationStatus`, `active`, `firstPayoutCompleted` quedan en el `@Builder.Default` de la entidad (hallazgo: no todos los ignorados quedan en null) | ✅ Pasa |
| `defaultMethodAndCertificateUrl_areIgnored` | `toPayoutMethodResponseDTO`: `defaultMethod`/`certificateUrl` sin setear (se resuelven en el service) | ✅ Pasa |
| `copiesRemainingFields` | Copia el resto de campos desde la entidad | ✅ Pasa |

---

## Wompi

### `WompiClientTest` — cubre `WompiClient` — 17 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `happyPath_parsesResponse` | `createTransaction`: la respuesta 200 se parsea correctamente | ✅ Pasa |
| `httpError_wrapsInWompiApiException` | Error HTTP se envuelve en `WompiApiException` con el status original | ✅ Pasa |
| `happyPath_returnsTransaction` | `getTransaction`: retorna la transacción consultada | ✅ Pasa |
| `notFound_throwsWompiApiExceptionWithSpecificMessage` | 404 se traduce a mensaje de "no encontrada" | ✅ Pasa |
| `serverError_wrapsWithGenericMessage` | Error 5xx distinto de 404: mensaje genérico | ✅ Pasa |
| `happyPath_returnsMostRecent` | `findTransactionByReference`: retorna la más reciente entre varias con la misma referencia | ✅ Pasa |
| `noResults_returnsNull` | Sin resultados: retorna null, no lanza | ✅ Pasa |
| `notFound_returnsNullInsteadOfThrowing` | 404: retorna null (a diferencia de `getTransaction`) | ✅ Pasa |
| `otherHttpError_wrapsInWompiApiException` | Error HTTP distinto de 404 se envuelve | ✅ Pasa |
| `buildsHashFromConcatenatedFields` | `generateIntegrityHash`: arma SHA-256 desde reference+amount+currency+secret | ✅ Pasa |
| `differentInputs_produceDifferentHash` | Distintos inputs producen distinto hash | ✅ Pasa |
| `correctChecksum_returnsTrue` | `isValidWebhookSignature`: checksum correcto retorna true | ✅ Pasa |
| `wrongChecksum_returnsFalse` | Checksum incorrecto retorna false | ✅ Pasa |
| `missingTimestamp_returnsFalse` | Payload sin timestamp: false, sin lanzar | ✅ Pasa |
| `missingSignatureBlock_returnsFalse` | Payload sin bloque signature: false | ✅ Pasa |
| `missingTransaction_returnsFalse` | Payload sin `data.transaction`: false | ✅ Pasa |
| `malformedJson_returnsFalseWithoutThrowing` | JSON malformado: se captura y retorna false | ✅ Pasa |

### `WompiServiceTest` — cubre `WompiService` — 12 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `happyPath_generatesUrlAndPersistsPendingRecord` | `createCheckoutUrl`: genera URL firmada, persiste registro PENDING | ✅ Pasa |
| `nullCustomerEmail_doesNotThrow` | `customerEmail` null: no lanza, guarda metadata con email vacío | ✅ Pasa |
| `existingNonPendingTransaction_returnsTrue` | `isAlreadyProcessed`: transacción existente no-PENDING → true | ✅ Pasa |
| `existingPendingTransaction_returnsFalse` | Transacción en PENDING → false | ✅ Pasa |
| `noLocalRecord_returnsFalse` | Sin registro local → false | ✅ Pasa |
| `wompiHasTransaction_returnsOptionalWithData` | `reconcileByReference`: Wompi tiene la transacción → Optional con datos | ✅ Pasa |
| `wompiHasNoTransaction_returnsEmptyOptional` | Wompi no la tiene → Optional vacío | ✅ Pasa |
| `approvedStatus_updatesAllFieldsIncludingWompiCreatedAt` | `updateTransactionFromWebhook` APPROVED: actualiza wompiId/status/metadata/wompiCreatedAt | ✅ Pasa |
| `declinedStatus_doesNotSetWompiCreatedAt` | DECLINED: actualiza status pero NO setea `wompiCreatedAt` | ✅ Pasa |
| `unknownStatus_mapsToError` | Status desconocido: se mapea a ERROR, no lanza | ✅ Pasa |
| `unknownReference_throwsIllegalArgumentException` | Referencia desconocida: lanza, no guarda nada | ✅ Pasa |
| `malformedWompiCreatedAt_doesNotThrowAndLeavesFieldNull` | `wompiCreatedAt` con formato inválido: no lanza, deja el campo null | ✅ Pasa |

### `WompiWebhookDispatcherTest` — cubre `WompiWebhookDispatcher` — 6 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `chargePlanSubscription_dispatchesToPlanService` | CHARGE_PLAN_SUBSCRIPTION despacha a `PlanService.handleWompiResult` | ✅ Pasa |
| `chargeBusinessDeposit_dispatchesToPlanService` | CHARGE_BUSINESS_DEPOSIT también despacha a `PlanService` (mismo handler) | ✅ Pasa |
| `chargeCopayment_dispatchesToCopaymentService` | CHARGE_COPAYMENT despacha a `CopaymentService` | ✅ Pasa |
| `transferPayout_dispatchesToPayoutService` | TRANSFER_PAYOUT despacha a `PayoutService` | ✅ Pasa |
| `payloadIsIgnoredForRouting_onlyTransactionTypeMatters` | El payload no afecta el enrutamiento, solo el tipo de transacción | ✅ Pasa |
| `exceptionFromTargetService_isCaughtAndDoesNotPropagate` | Excepción del servicio destino se captura dentro de `dispatch`, no se propaga | ✅ Pasa |

---

## Controllers (Mockito puro, sin `@WebMvcTest`) — los 7 controllers que hoy no tenían ningún test

### `PlanChangeRequestControllerTest` — cubre `PlanChangeRequestController` — 9 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `previewPlanChange_delegates` | `GET /preview`: delega con el commercialId del JWT y los query params | ✅ Pasa |
| `previewPlanChange_nullInvestmentAmount` | Acepta `intendedInvestmentAmountCents` nulo | ✅ Pasa |
| `requestPlanChange_delegatesAndMaps` | `POST /`: delega y mapea la entidad devuelta a DTO | ✅ Pasa |
| `requestPlanChange_handlesNullFromPlanAndContract` | No explota cuando `fromPlan` y `contract` son null | ✅ Pasa |
| `getCurrent_returnsMapped` | `GET /current`: mapea la solicitud actual cuando existe | ✅ Pasa |
| `getCurrent_returnsNullBodyWhenNoCurrent` | Devuelve body null cuando no hay solicitud actual | ✅ Pasa |
| `cancel_delegates` | `POST /{id}/cancel`: delega con commercialId e id del path | ✅ Pasa |
| `approveContract_delegatesToContractService` | `POST /contract/{contractId}/approve`: delega en `CommercialContractService`, no en `PlanChangeRequestService` | ✅ Pasa |
| `generateTopUpCheckout_delegates` | `POST /{id}/top-up-checkout`: resuelve el commercial del JWT, delega en `PlanService` | ✅ Pasa |

### `PayoutAdminControllerTest` — cubre `PayoutAdminController` — 5 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getPayoutsForDate_withExplicitDate` | `GET /`: con fecha explícita, delega con esa fecha | ✅ Pasa |
| `getPayoutsForDate_withoutDate_usesToday` | Sin fecha, usa el día de hoy | ✅ Pasa |
| `runNow_schedulesAndProcesses` | `POST /run-now`: dispara `scheduleDailyPayouts` seguido de `processScheduledPayouts`, periodo de 1 día | ✅ Pasa |
| `retryNow_delegates` | `POST /retry-now`: delega en `retryFailedPayouts` | ✅ Pasa |
| `getWompiStatus_delegates` | `GET /{id}/wompi-status`: delega con el id del path | ✅ Pasa |

### `PayoutMethodAdminControllerTest` — cubre `PayoutMethodAdminController` — 5 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getByStatus_delegates` | `GET /`: delega con el status y el Pageable armado de page/size | ✅ Pasa |
| `getByStatus_customPageAndStatus` | Respeta page/size custom y otro status | ✅ Pasa |
| `verify_delegates` | `POST /{id}/verify`: delega en `adminVerifyMethod` | ✅ Pasa |
| `reject_delegates` | `POST /{id}/reject`: delega en `adminRejectMethod` con el motivo del body | ✅ Pasa |
| `getCertificate_delegates` | `GET /{id}/certificate`: delega en `streamCertificate` con el response del servlet | ✅ Pasa |

### `TreasuryAdminControllerTest` — cubre `TreasuryAdminController` — 3 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getBalance_delegates` | `GET /balance`: delega en `TreasuryService.getBalanceReport` | ✅ Pasa |
| `getMovements_delegates` | `GET /movements/{code}`: delega con el code y el Pageable, envuelto en `PagedResponse` | ✅ Pasa |
| `getKeysReservePct_delegates` | `GET /config/keys-reserve-pct`: delega en `TreasuryConfig` (endpoint con `@PreAuthorize` propio: ADMIN o COMMERCIAL — no verificable a nivel unitario) | ✅ Pasa |

### `PayoutMethodControllerTest` — cubre `PayoutMethodController` — 9 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `create_delegates` | `POST /`: crea el método de pago para el commercial del JWT, responde 201 | ✅ Pasa |
| `getBanks_delegates` | `GET /banks`: devuelve el catálogo de bancos del service | ✅ Pasa |
| `verifyOtp_delegates` | `POST /{id}/verify-otp`: delega con commercial, id y código OTP | ✅ Pasa |
| `resendOtp_delegates` | `POST /{id}/resend-otp`: delega con commercial e id | ✅ Pasa |
| `getAll_delegates` | `GET /`: delega con commercial y pageable de page/size | ✅ Pasa |
| `deactivate_delegates` | `PUT /{id}/deactivate`: delega con commercial e id | ✅ Pasa |
| `setDefault_delegates` | `PUT /{id}/set-default`: delega con commercial e id | ✅ Pasa |
| `prepareCertificateUpload_delegates` | `POST /{id}/certificate/prepare`: delega con commercial, id y metadatos del archivo | ✅ Pasa |
| `confirmCertificateUpload_delegates` | `POST /{id}/certificate/confirm`: delega con commercial, id y id del asset | ✅ Pasa |

### `WalletControllerTest` — cubre `WalletController` — 3 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getBillingSummary_delegates` | `GET /me/billing-summary`: resuelve el commercial del JWT y delega | ✅ Pasa |
| `getDeposits_delegates` | `GET /me/deposits`: delega con año, mes y pageable | ✅ Pasa |
| `getPayouts_delegates` | `GET /me/payouts`: delega con año, mes y pageable | ✅ Pasa |

### `KeyWalletControllerTest` — cubre `KeyWalletController` — 2 pruebas

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getBalance_delegates` | `GET /balance`: resuelve el consumer del JWT y delega | ✅ Pasa |
| `spend_delegates` | `POST /spend`: resuelve el consumer del JWT y delega con el request | ✅ Pasa |

---

## Resumen

- **Clases de test**: 15 (todas nuevas).
- **Pruebas individuales listadas en este documento**: ~168.
- **Total del dominio finance (unitarias + integración + repositorio)**: 395 pruebas, 0 fallos, 0 errores, 2 skipped (documentadas en `FINANCE_REPOSITORY_TESTS.md`) — `mvn test`, 2026-08-27.
