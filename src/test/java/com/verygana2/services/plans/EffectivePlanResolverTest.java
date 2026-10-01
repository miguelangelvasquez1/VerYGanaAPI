package com.verygana2.services.plans;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.EffectivePlanState;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.finance.plans.PlanFeature;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.finance.plans.PlanFeatureRepository;
import com.verygana2.services.plans.EffectivePlanResolver.BudgetThresholds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link EffectivePlanResolver}: resolución del estado efectivo del
 * comercial (sin plan / BASIC / STANDARD-PREMIUM), la fuente del saldo
 * (wallet vs. hardcodeado a 0 en BASIC), la resolución de features con
 * override de onboarding vs. PlanFeature vs. default hardcodeado, el flag
 * budgetSuspended, y los umbrales de saldo bajo.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EffectivePlanResolver")
class EffectivePlanResolverTest {

    @Mock private CommercialDetailsRepository commercialDetailsRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private PlanFeatureRepository planFeatureRepository;

    private EffectivePlanResolver resolver;

    private static final Long COMMERCIAL_ID = 1L;

    @BeforeEach
    void setUp() {
        resolver = new EffectivePlanResolver(commercialDetailsRepository, walletRepository, planFeatureRepository);
        // Campos @Value: fuera de un contexto Spring hay que fijarlos a mano (ver convención del proyecto).
        ReflectionTestUtils.setField(resolver, "defaultWarningPct", new BigDecimal("10"));
        ReflectionTestUtils.setField(resolver, "defaultCriticalPct", BigDecimal.ZERO);
        ReflectionTestUtils.setField(resolver, "defaultGracePeriodDays", 15);
    }

    private Plan plan(PlanCode code) {
        return Plan.builder().code(code).name(code.name()).saleCommissionPct(10).build();
    }

    private CommercialDetails commercial(Plan plan, CommercialOnboarding onboarding) {
        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(COMMERCIAL_ID);
        commercial.setCurrentPlan(plan);
        commercial.setOnboarding(onboarding);
        return commercial;
    }

    private PlanFeature boolFeature(boolean value) {
        return PlanFeature.builder().boolValue(value).build();
    }

    private PlanFeature intFeature(int value) {
        return PlanFeature.builder().intValue(value).build();
    }

    private PlanFeature decimalFeature(BigDecimal value) {
        return PlanFeature.builder().decimalValue(value).build();
    }

    // ─── resolve: comercial / plan ──────────────────────────────────────────────

    @Nested
    @DisplayName("resolve")
    class Resolve {

        @Test
        @DisplayName("comercial no encontrado: lanza IllegalArgumentException")
        void commercialNotFound_throwsIllegalArgumentException() {
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> resolver.resolve(COMMERCIAL_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("currentPlan == null: retorna EffectivePlanState.noPlanMode()")
        void noCurrentPlan_returnsNoPlanMode() {
            CommercialDetails commercial = commercial(null, null);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            EffectivePlanState expected = EffectivePlanState.noPlanMode();
            assertThat(result).isEqualTo(expected);
            assertThat(result.isHasActivePlan()).isFalse();
            assertThat(result.isBudgetSuspended()).isTrue();
            assertThat(result.isCanAdvertise()).isFalse();
            assertThat(result.getMaxProducts()).isZero();
            assertThat(result.getVisibilityBoostPct()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("plan BASIC: balanceCents=0 y nunca consulta el WalletRepository")
        void basicPlan_neverTouchesWallet() {
            CommercialDetails commercial = commercial(plan(PlanCode.BASIC), null);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            assertThat(result.getRemainingBudget()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(walletRepository, never()).findByCommercialId(any());
        }

        @Test
        @DisplayName("plan STANDARD/PREMIUM con wallet: lee balanceCents del wallet")
        void standardPlan_withWallet_readsBalanceFromWallet() {
            CommercialDetails commercial = commercial(plan(PlanCode.STANDARD), null);
            Wallet wallet = new Wallet();
            wallet.setBalanceCents(150_000L);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
            when(walletRepository.findByCommercialId(COMMERCIAL_ID)).thenReturn(Optional.of(wallet));

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            assertThat(result.getRemainingBudget()).isEqualByComparingTo(new BigDecimal("1500.00"));
        }

        @Test
        @DisplayName("plan STANDARD/PREMIUM sin wallet: usa 0")
        void standardPlan_withoutWallet_usesZero() {
            CommercialDetails commercial = commercial(plan(PlanCode.PREMIUM), null);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
            when(walletRepository.findByCommercialId(COMMERCIAL_ID)).thenReturn(Optional.empty());

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            assertThat(result.getRemainingBudget()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ─── budgetSuspended ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("budgetSuspended")
    class BudgetSuspended {

        @Test
        @DisplayName("BASIC nunca está suspendido, aunque su balance sea 0")
        void basic_neverSuspended() {
            CommercialDetails commercial = commercial(plan(PlanCode.BASIC), null);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            assertThat(result.isBudgetSuspended()).isFalse();
        }

        @Test
        @DisplayName("STANDARD/PREMIUM con balance 0: suspendido")
        void nonBasicZeroBalance_suspended() {
            CommercialDetails commercial = commercial(plan(PlanCode.STANDARD), null);
            Wallet wallet = new Wallet();
            wallet.setBalanceCents(0L);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
            when(walletRepository.findByCommercialId(COMMERCIAL_ID)).thenReturn(Optional.of(wallet));

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            assertThat(result.isBudgetSuspended()).isTrue();
        }

        @Test
        @DisplayName("STANDARD/PREMIUM con balance > 0: no suspendido")
        void nonBasicPositiveBalance_notSuspended() {
            CommercialDetails commercial = commercial(plan(PlanCode.PREMIUM), null);
            Wallet wallet = new Wallet();
            wallet.setBalanceCents(1L);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
            when(walletRepository.findByCommercialId(COMMERCIAL_ID)).thenReturn(Optional.of(wallet));

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            assertThat(result.isBudgetSuspended()).isFalse();
        }
    }

    // ─── Features: booleanas / enteras / decimal — override vs. PlanFeature ────
    // Se usa BASIC para no involucrar al WalletRepository (no relevante aquí).

    @Nested
    @DisplayName("Features booleanas (representadas por canAdvertise)")
    class BooleanFeature {

        @Test
        @DisplayName("con override del onboarding: usa el override e ignora PlanFeature")
        void withOverride_usesOverride() {
            CommercialOnboarding onboarding = new CommercialOnboarding();
            onboarding.setCanAdvertiseOverride(true);
            CommercialDetails commercial = commercial(plan(PlanCode.BASIC), onboarding);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            assertThat(result.isCanAdvertise()).isTrue();
            verify(planFeatureRepository, never()).findByPlanCodeAndFeatureCode(eq(PlanCode.BASIC), eq("CAN_ADVERTISE"));
        }

        @Test
        @DisplayName("sin override: cae al PlanFeature del plan cuando existe")
        void withoutOverride_fallsBackToPlanFeature() {
            CommercialDetails commercial = commercial(plan(PlanCode.BASIC), null);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.BASIC, "CAN_ADVERTISE"))
                    .thenReturn(Optional.of(boolFeature(true)));

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            assertThat(result.isCanAdvertise()).isTrue();
        }

        @Test
        @DisplayName("sin override y sin PlanFeature: cae al default hardcodeado (false)")
        void withoutOverrideOrPlanFeature_usesHardcodedDefault() {
            CommercialDetails commercial = commercial(plan(PlanCode.BASIC), null);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.BASIC, "CAN_ADVERTISE"))
                    .thenReturn(Optional.empty());

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            assertThat(result.isCanAdvertise()).isFalse();
        }
    }

    @Nested
    @DisplayName("Features enteras (representadas por maxProducts)")
    class IntFeature {

        @Test
        @DisplayName("con override del onboarding: usa el override e ignora PlanFeature")
        void withOverride_usesOverride() {
            CommercialOnboarding onboarding = new CommercialOnboarding();
            onboarding.setMaxProductsOverride(5);
            CommercialDetails commercial = commercial(plan(PlanCode.BASIC), onboarding);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            assertThat(result.getMaxProducts()).isEqualTo(5);
            verify(planFeatureRepository, never()).findByPlanCodeAndFeatureCode(eq(PlanCode.BASIC), eq("MAX_PRODUCTS"));
        }

        @Test
        @DisplayName("sin override: cae al PlanFeature del plan cuando existe")
        void withoutOverride_fallsBackToPlanFeature() {
            CommercialDetails commercial = commercial(plan(PlanCode.BASIC), null);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
            lenient().when(planFeatureRepository.findByPlanCodeAndFeatureCode(any(), any())).thenReturn(Optional.empty());
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.BASIC, "MAX_PRODUCTS"))
                    .thenReturn(Optional.of(intFeature(50)));

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            assertThat(result.getMaxProducts()).isEqualTo(50);
        }

        @Test
        @DisplayName("sin override y sin PlanFeature: cae al default hardcodeado (0)")
        void withoutOverrideOrPlanFeature_usesHardcodedDefault() {
            CommercialDetails commercial = commercial(plan(PlanCode.BASIC), null);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
            lenient().when(planFeatureRepository.findByPlanCodeAndFeatureCode(any(), any())).thenReturn(Optional.empty());
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.BASIC, "MAX_PRODUCTS"))
                    .thenReturn(Optional.empty());

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            assertThat(result.getMaxProducts()).isZero();
        }
    }

    @Nested
    @DisplayName("Feature decimal (visibilityBoostPct)")
    class DecimalFeature {

        @Test
        @DisplayName("con override del onboarding: usa el override e ignora PlanFeature")
        void withOverride_usesOverride() {
            CommercialOnboarding onboarding = new CommercialOnboarding();
            onboarding.setVisibilityBoostPctOverride(new BigDecimal("15.00"));
            CommercialDetails commercial = commercial(plan(PlanCode.BASIC), onboarding);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            assertThat(result.getVisibilityBoostPct()).isEqualByComparingTo(new BigDecimal("15.00"));
            verify(planFeatureRepository, never()).findByPlanCodeAndFeatureCode(eq(PlanCode.BASIC), eq("VISIBILITY_BOOST"));
        }

        @Test
        @DisplayName("sin override: cae al PlanFeature del plan cuando existe")
        void withoutOverride_fallsBackToPlanFeature() {
            CommercialDetails commercial = commercial(plan(PlanCode.BASIC), null);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
            lenient().when(planFeatureRepository.findByPlanCodeAndFeatureCode(any(), any())).thenReturn(Optional.empty());
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.BASIC, "VISIBILITY_BOOST"))
                    .thenReturn(Optional.of(decimalFeature(new BigDecimal("7.50"))));

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            assertThat(result.getVisibilityBoostPct()).isEqualByComparingTo(new BigDecimal("7.50"));
        }

        @Test
        @DisplayName("sin override y sin PlanFeature: cae al default hardcodeado (0)")
        void withoutOverrideOrPlanFeature_usesHardcodedDefault() {
            CommercialDetails commercial = commercial(plan(PlanCode.BASIC), null);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
            lenient().when(planFeatureRepository.findByPlanCodeAndFeatureCode(any(), any())).thenReturn(Optional.empty());
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.BASIC, "VISIBILITY_BOOST"))
                    .thenReturn(Optional.empty());

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            assertThat(result.getVisibilityBoostPct()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("canExportReport (sin override de onboarding disponible)")
    class CanExportReportFeature {

        @Test
        @DisplayName("PlanFeature presente: usa su valor")
        void planFeaturePresent_usesItsValue() {
            CommercialDetails commercial = commercial(plan(PlanCode.BASIC), null);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
            lenient().when(planFeatureRepository.findByPlanCodeAndFeatureCode(any(), any())).thenReturn(Optional.empty());
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.BASIC, "CAN_EXPORT_REPORT"))
                    .thenReturn(Optional.of(boolFeature(true)));

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            assertThat(result.isCanExportReport()).isTrue();
        }

        @Test
        @DisplayName("PlanFeature ausente: cae al default hardcodeado (false)")
        void planFeatureAbsent_usesHardcodedDefault() {
            CommercialDetails commercial = commercial(plan(PlanCode.BASIC), null);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
            lenient().when(planFeatureRepository.findByPlanCodeAndFeatureCode(any(), any())).thenReturn(Optional.empty());
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.BASIC, "CAN_EXPORT_REPORT"))
                    .thenReturn(Optional.empty());

            EffectivePlanState result = resolver.resolve(COMMERCIAL_ID);

            assertThat(result.isCanExportReport()).isFalse();
        }
    }

    // ─── resolveBudgetThresholds ────────────────────────────────────────────────

    @Nested
    @DisplayName("resolveBudgetThresholds")
    class ResolveBudgetThresholds {

        private Wallet walletFor(CommercialDetails commercial, long lastDepositAmountCents) {
            Wallet wallet = new Wallet();
            wallet.setCommercial(commercial);
            wallet.setLastDepositAmountCents(lastDepositAmountCents);
            return wallet;
        }

        @Test
        @DisplayName("sin plan: retorna (0,0)")
        void noPlan_returnsZeroZero() {
            CommercialDetails commercial = commercial(null, null);
            Wallet wallet = walletFor(commercial, 100_000L);

            BudgetThresholds thresholds = resolver.resolveBudgetThresholds(wallet);

            assertThat(thresholds).isEqualTo(new BudgetThresholds(0L, 0L));
        }

        @Test
        @DisplayName("plan BASIC: retorna (0,0)")
        void basicPlan_returnsZeroZero() {
            CommercialDetails commercial = commercial(plan(PlanCode.BASIC), null);
            Wallet wallet = walletFor(commercial, 100_000L);

            BudgetThresholds thresholds = resolver.resolveBudgetThresholds(wallet);

            assertThat(thresholds).isEqualTo(new BudgetThresholds(0L, 0L));
        }

        @Test
        @DisplayName("plan no-BASIC: prioriza el monto fijo (LOW_BALANCE_WARNING_FIXED_CENTS) sobre el porcentaje")
        void nonBasicWithFixedCents_prioritizesFixedOverPct() {
            CommercialDetails commercial = commercial(plan(PlanCode.STANDARD), null);
            Wallet wallet = walletFor(commercial, 100_000L);
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.STANDARD, "LOW_BALANCE_WARNING_FIXED_CENTS"))
                    .thenReturn(Optional.of(PlanFeature.builder().longValue(5_000L).build()));
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.STANDARD, "LOW_BALANCE_CRITICAL_PCT"))
                    .thenReturn(Optional.empty());

            BudgetThresholds thresholds = resolver.resolveBudgetThresholds(wallet);

            assertThat(thresholds.warningCents()).isEqualTo(5_000L);
            assertThat(thresholds.criticalCents()).isZero();
        }

        @Test
        @DisplayName("plan no-BASIC sin monto fijo: usa el porcentaje configurado (LOW_BALANCE_WARNING_PCT)")
        void nonBasicWithoutFixedCents_usesConfiguredPct() {
            CommercialDetails commercial = commercial(plan(PlanCode.STANDARD), null);
            Wallet wallet = walletFor(commercial, 100_000L);
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.STANDARD, "LOW_BALANCE_WARNING_FIXED_CENTS"))
                    .thenReturn(Optional.empty());
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.STANDARD, "LOW_BALANCE_WARNING_PCT"))
                    .thenReturn(Optional.of(PlanFeature.builder().decimalValue(new BigDecimal("20")).build()));
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.STANDARD, "LOW_BALANCE_CRITICAL_PCT"))
                    .thenReturn(Optional.empty());

            BudgetThresholds thresholds = resolver.resolveBudgetThresholds(wallet);

            assertThat(thresholds.warningCents()).isEqualTo(20_000L); // 100_000 * 20%
        }

        @Test
        @DisplayName("plan no-BASIC sin monto fijo ni porcentaje configurado: usa el default de la aplicación (budget.low-balance-warning-pct)")
        void nonBasicWithoutFixedOrConfiguredPct_fallsBackToWalletPct() {
            CommercialDetails commercial = commercial(plan(PlanCode.STANDARD), null);
            Wallet wallet = walletFor(commercial, 100_000L);
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.STANDARD, "LOW_BALANCE_WARNING_FIXED_CENTS"))
                    .thenReturn(Optional.empty());
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.STANDARD, "LOW_BALANCE_WARNING_PCT"))
                    .thenReturn(Optional.empty());
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.STANDARD, "LOW_BALANCE_CRITICAL_PCT"))
                    .thenReturn(Optional.empty());

            BudgetThresholds thresholds = resolver.resolveBudgetThresholds(wallet);

            assertThat(thresholds.warningCents()).isEqualTo(10_000L); // 100_000 * 10% (default de la app fijado en setUp)
        }

        @Test
        @DisplayName("criticalCents solo se calcula cuando LOW_BALANCE_CRITICAL_PCT > 0")
        void criticalCents_onlyComputedWhenPctPositive() {
            CommercialDetails commercial = commercial(plan(PlanCode.PREMIUM), null);
            Wallet wallet = walletFor(commercial, 100_000L);
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.PREMIUM, "LOW_BALANCE_WARNING_FIXED_CENTS"))
                    .thenReturn(Optional.empty());
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.PREMIUM, "LOW_BALANCE_WARNING_PCT"))
                    .thenReturn(Optional.empty());
            when(planFeatureRepository.findByPlanCodeAndFeatureCode(PlanCode.PREMIUM, "LOW_BALANCE_CRITICAL_PCT"))
                    .thenReturn(Optional.of(PlanFeature.builder().decimalValue(new BigDecimal("5")).build()));

            BudgetThresholds thresholds = resolver.resolveBudgetThresholds(wallet);

            assertThat(thresholds.criticalCents()).isEqualTo(5_000L); // 100_000 * 5%
        }
    }
}
