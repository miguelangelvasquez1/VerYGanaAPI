package com.verygana2.services.plans;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.EffectivePlanState;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.finance.plans.PlanFeatureRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resuelve el estado efectivo de un comercial en tiempo real.
 *
 * Algoritmo:
 *  1. Leer commercial.currentPlan — si null → SIN PLAN.
 *  2. Si BASIC → features de BASIC, remainingBudget = 0.
 *  3. Si STANDARD/PREMIUM → leer wallet.balanceCents para remainingBudget.
 *  4. Features (canAdvertise, maxProducts, etc.) siempre vienen de PlanFeature.
 *  5. Comisión siempre activa cuando hay plan (sin regla de ROI).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EffectivePlanResolver {

    private static final String FEAT_CAN_ADVERTISE     = "CAN_ADVERTISE";
    private static final String FEAT_CAN_USE_GAMES     = "CAN_USE_GAMES";
    private static final String FEAT_CAN_USE_SURVEYS   = "CAN_USE_SURVEYS";
    private static final String FEAT_CAN_SELL_DIRECTLY = "CAN_SELL_DIRECTLY";
    private static final String FEAT_CAN_HAVE_PETS     = "CAN_HAVE_PETS";
    private static final String FEAT_CAN_PROMOTE_ALLY_PRODUCTS = "CAN_PROMOTE_ALLY_PRODUCTS";
    private static final String FEAT_CAN_EXPORT_REPORT = "CAN_EXPORT_REPORT";
    private static final String FEAT_CAN_VIEW_PERFORMANCE_METRICS = "CAN_VIEW_PERFORMANCE_METRICS";
    private static final String FEAT_CAN_VIEW_PAGE_VISIT_METRICS   = "CAN_VIEW_PAGE_VISIT_METRICS";
    private static final String FEAT_MAX_PRODUCTS      = "MAX_PRODUCTS";
    private static final String FEAT_MAX_ADS           = "MAX_ADS";
    private static final String FEAT_MAX_BRANDED_GAMES = "MAX_BRANDED_GAMES";
    private static final String FEAT_MAX_SURVEYS       = "MAX_SURVEYS";
    private static final String FEAT_VISIBILITY_BOOST  = "VISIBILITY_BOOST";
    private static final String FEAT_LOW_BALANCE_WARNING_PCT      = "LOW_BALANCE_WARNING_PCT";
    private static final String FEAT_LOW_BALANCE_CRITICAL_PCT     = "LOW_BALANCE_CRITICAL_PCT";
    private static final String FEAT_LOW_BALANCE_WARNING_FIXED_CENTS = "LOW_BALANCE_WARNING_FIXED_CENTS";
    private static final String FEAT_BUDGET_GRACE_PERIOD_DAYS = "BUDGET_GRACE_PERIOD_DAYS";

    private static final BigDecimal CENTS_PER_COP = BigDecimal.valueOf(100);

    private final CommercialDetailsRepository commercialDetailsRepository;
    private final WalletRepository walletRepository;
    private final PlanFeatureRepository planFeatureRepository;

    /** Días de gracia con saldo agotado antes de DORMANT, cuando el plan no define BUDGET_GRACE_PERIOD_DAYS. */
    @Value("${budget.grace-period-days:15}")
    private int defaultGracePeriodDays;

    /** % del último depósito para el aviso WARNING, cuando el plan no define LOW_BALANCE_WARNING_PCT. */
    @Value("${budget.low-balance-warning-pct:20}")
    private BigDecimal defaultWarningPct;

    /** % del último depósito para el aviso CRITICAL, cuando el plan no define LOW_BALANCE_CRITICAL_PCT. 0 = sin escalón. */
    @Value("${budget.low-balance-critical-pct:5}")
    private BigDecimal defaultCriticalPct;

    @Transactional(readOnly = true)
    public EffectivePlanState resolve(Long commercialId) {

        CommercialDetails commercial = commercialDetailsRepository.findById(commercialId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Comercial no encontrado: " + commercialId));

        Plan currentPlan = commercial.getCurrentPlan();

        if (currentPlan == null) {
            log.debug("Comercial {} → SIN PLAN", commercialId);
            return EffectivePlanState.noPlanMode();
        }

        long balanceCents = 0L;
        ZonedDateTime exhaustedSince = null;
        if (currentPlan.getCode() != PlanCode.BASIC) {
            Optional<Wallet> wallet = walletRepository.findByCommercialId(commercialId);
            balanceCents = wallet.map(Wallet::getBalanceCents).orElse(0L);
            exhaustedSince = wallet.map(Wallet::getExhaustedSince).orElse(null);
        }

        log.debug("Comercial {} → {} (saldo: {} centavos)", commercialId,
                currentPlan.getCode(), balanceCents);

        return buildStateForPlan(currentPlan, balanceCents, exhaustedSince, commercial.getOnboarding());
    }

    // ── Builder de estado ─────────────────────────────────────────────────────

    private EffectivePlanState buildStateForPlan(Plan plan, long balanceCents,
            ZonedDateTime exhaustedSince, CommercialOnboarding onboarding) {
        PlanCode code = plan.getCode();
        BigDecimal remainingCOP = BigDecimal.valueOf(balanceCents)
                .divide(CENTS_PER_COP, 2, RoundingMode.HALF_UP);

        boolean budgetSuspended = code != PlanCode.BASIC && balanceCents == 0L;
        boolean budgetDormant = budgetSuspended && exhaustedSince != null
                && exhaustedSince.isBefore(
                        ZonedDateTime.now(ZoneOffset.UTC).minusDays(resolveGracePeriodDays(plan)));

        return EffectivePlanState.builder()
                .hasActivePlan(true)
                .effectivePlan(code)
                .commissionActive(plan.getSaleCommissionPct() > 0)
                .commissionRate(BigDecimal.valueOf(plan.getSaleCommissionPct()))
                .remainingBudget(remainingCOP)
                .canAdvertise(resolveBool(onboarding == null ? null : onboarding.getCanAdvertiseOverride(),
                        () -> getFeatureBool(code, FEAT_CAN_ADVERTISE, false)))
                .canUseGames(resolveBool(onboarding == null ? null : onboarding.getCanUseGamesOverride(),
                        () -> getFeatureBool(code, FEAT_CAN_USE_GAMES, false)))
                .canUseSurveys(resolveBool(onboarding == null ? null : onboarding.getCanUseSurveysOverride(),
                        () -> getFeatureBool(code, FEAT_CAN_USE_SURVEYS, false)))
                .canSellDirectly(resolveBool(onboarding == null ? null : onboarding.getCanSellDirectlyOverride(),
                        () -> getFeatureBool(code, FEAT_CAN_SELL_DIRECTLY, false)))
                .canHavePets(resolveBool(onboarding == null ? null : onboarding.getCanHavePetsOverride(),
                        () -> getFeatureBool(code, FEAT_CAN_HAVE_PETS, false)))
                .canPromoteAllyProducts(resolveBool(onboarding == null ? null : onboarding.getCanPromoteAllyProductsOverride(),
                        () -> getFeatureBool(code, FEAT_CAN_PROMOTE_ALLY_PRODUCTS, false)))
                // Sin override de onboarding todavía — solo depende del feature del Plan.
                .canExportReport(getFeatureBool(code, FEAT_CAN_EXPORT_REPORT, false))
                .canViewPerformanceMetrics(getFeatureBool(code, FEAT_CAN_VIEW_PERFORMANCE_METRICS, false))
                .canViewPageVisitMetrics(getFeatureBool(code, FEAT_CAN_VIEW_PAGE_VISIT_METRICS, false))
                .budgetSuspended(budgetSuspended)
                .budgetDormant(budgetDormant)
                .maxProducts(resolveInt(onboarding == null ? null : onboarding.getMaxProductsOverride(),
                        () -> getFeatureInt(code, FEAT_MAX_PRODUCTS, 0)))
                .maxAds(resolveInt(onboarding == null ? null : onboarding.getMaxAdsOverride(),
                        () -> getFeatureInt(code, FEAT_MAX_ADS, 0)))
                .maxBrandedGames(resolveInt(onboarding == null ? null : onboarding.getMaxBrandedGamesOverride(),
                        () -> getFeatureInt(code, FEAT_MAX_BRANDED_GAMES, 0)))
                .maxSurveys(resolveInt(onboarding == null ? null : onboarding.getMaxSurveysOverride(),
                        () -> getFeatureInt(code, FEAT_MAX_SURVEYS, 0)))
                .visibilityBoostPct(resolveDecimal(onboarding == null ? null : onboarding.getVisibilityBoostPctOverride(),
                        () -> getFeatureDecimal(code, FEAT_VISIBILITY_BOOST, BigDecimal.ZERO)))
                .build();
    }

    /**
     * Un override del onboarding (negociación Ruta E) pisa el valor del Plan
     * para ese empresario en particular; null = sin override, se usa el Plan.
     */
    private boolean resolveBool(Boolean override, java.util.function.BooleanSupplier planValue) {
        return override != null ? override : planValue.getAsBoolean();
    }

    private int resolveInt(Integer override, java.util.function.IntSupplier planValue) {
        return override != null ? override : planValue.getAsInt();
    }

    private BigDecimal resolveDecimal(BigDecimal override, java.util.function.Supplier<BigDecimal> planValue) {
        return override != null ? override : planValue.get();
    }

    // ── Helpers de feature ────────────────────────────────────────────────────

    private boolean getFeatureBool(PlanCode planCode, String featureCode, boolean defaultVal) {
        return planFeatureRepository.findByPlanCodeAndFeatureCode(planCode, featureCode)
                .map(pf -> pf.getBoolOrDefault(defaultVal))
                .orElse(defaultVal);
    }

    private int getFeatureInt(PlanCode planCode, String featureCode, int defaultVal) {
        return planFeatureRepository.findByPlanCodeAndFeatureCode(planCode, featureCode)
                .map(pf -> pf.getIntOrDefault(defaultVal))
                .orElse(defaultVal);
    }

    private BigDecimal getFeatureDecimal(PlanCode planCode, String featureCode, BigDecimal defaultVal) {
        return planFeatureRepository.findByPlanCodeAndFeatureCode(planCode, featureCode)
                .map(pf -> pf.getDecimalOrDefault(defaultVal))
                .orElse(defaultVal);
    }

    private long getFeatureLong(PlanCode planCode, String featureCode, long defaultVal) {
        return planFeatureRepository.findByPlanCodeAndFeatureCode(planCode, featureCode)
                .map(pf -> pf.getLongOrDefault(defaultVal))
                .orElse(defaultVal);
    }

    // ── Umbrales de saldo bajo (configurables por plan) ─────────────────────────

    /** Umbrales de aviso de saldo bajo, en centavos, para el plan/wallet dados. */
    public record BudgetThresholds(long warningCents, long criticalCents) {}

    /**
     * Resuelve los umbrales de aviso de saldo bajo para un wallet: primero busca un
     * monto fijo configurado por plan (LOW_BALANCE_WARNING_FIXED_CENTS); si no existe,
     * usa un porcentaje del último depósito (feature LOW_BALANCE_WARNING_PCT /
     * LOW_BALANCE_CRITICAL_PCT del plan, con fallback a budget.low-balance-*-pct).
     * Si el wallet nunca registró un depósito (lastDepositAmountCents null/0) → (0, 0).
     */
    public BudgetThresholds resolveBudgetThresholds(Wallet wallet) {
        CommercialDetails commercial = wallet.getCommercial();
        Plan plan = commercial.getCurrentPlan();
        if (plan == null || plan.getCode() == PlanCode.BASIC) {
            return new BudgetThresholds(0L, 0L);
        }

        long lastDeposit = wallet.getLastDepositAmountCents() != null ? wallet.getLastDepositAmountCents() : 0L;

        long fixedWarningCents = getFeatureLong(plan.getCode(), FEAT_LOW_BALANCE_WARNING_FIXED_CENTS, 0L);
        long warningCents = fixedWarningCents > 0
                ? fixedWarningCents
                : pctOf(lastDeposit, getFeatureDecimal(plan.getCode(), FEAT_LOW_BALANCE_WARNING_PCT, defaultWarningPct));

        BigDecimal criticalPct = getFeatureDecimal(plan.getCode(), FEAT_LOW_BALANCE_CRITICAL_PCT, defaultCriticalPct);
        long criticalCents = criticalPct.signum() > 0 ? pctOf(lastDeposit, criticalPct) : 0L;

        return new BudgetThresholds(warningCents, criticalCents);
    }

    /**
     * Días que una billetera STANDARD/PREMIUM puede permanecer en saldo cero antes de
     * pasar a estado DORMANT (bloqueo de edición). Configurable por plan vía el feature
     * {@code BUDGET_GRACE_PERIOD_DAYS}; si el plan no lo define se usa
     * {@code budget.grace-period-days}. BASIC / sin plan → 0.
     */
    public int resolveGracePeriodDays(Plan plan) {
        if (plan == null || plan.getCode() == PlanCode.BASIC) {
            return 0;
        }
        return getFeatureInt(plan.getCode(), FEAT_BUDGET_GRACE_PERIOD_DAYS, defaultGracePeriodDays);
    }

    private long pctOf(long amountCents, BigDecimal pct) {
        return BigDecimal.valueOf(amountCents).multiply(pct)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();
    }
}
