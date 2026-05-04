package com.verygana2.services.plans;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.EffectivePlanState;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.plans.PlanFeatureRepository;

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
    private static final String FEAT_MAX_PRODUCTS      = "MAX_PRODUCTS";
    private static final String FEAT_MAX_ADS           = "MAX_ADS";
    private static final String FEAT_MAX_BRANDED_GAMES = "MAX_BRANDED_GAMES";
    private static final String FEAT_MAX_SURVEYS       = "MAX_SURVEYS";

    private static final BigDecimal CENTS_PER_COP = BigDecimal.valueOf(100);

    private final CommercialDetailsRepository commercialDetailsRepository;
    private final WalletRepository walletRepository;
    private final PlanFeatureRepository planFeatureRepository;

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
        if (currentPlan.getCode() != PlanCode.BASIC) {
            balanceCents = walletRepository.findByCommercialId(commercialId)
                    .map(Wallet::getBalanceCents)
                    .orElse(0L);
        }

        log.debug("Comercial {} → {} (saldo: {} centavos)", commercialId,
                currentPlan.getCode(), balanceCents);

        return buildStateForPlan(currentPlan, balanceCents);
    }

    // ── Builder de estado ─────────────────────────────────────────────────────

    private EffectivePlanState buildStateForPlan(Plan plan, long balanceCents) {
        PlanCode code = plan.getCode();
        BigDecimal remainingCOP = BigDecimal.valueOf(balanceCents)
                .divide(CENTS_PER_COP, 2, RoundingMode.HALF_UP);

        return EffectivePlanState.builder()
                .hasActivePlan(true)
                .effectivePlan(code)
                .commissionActive(plan.getCommissionPerSale() > 0)
                .commissionRate(BigDecimal.valueOf(plan.getCommissionPerSale()))
                .remainingBudget(remainingCOP)
                .canAdvertise(getFeatureBool(code, FEAT_CAN_ADVERTISE, false))
                .canUseGames(getFeatureBool(code, FEAT_CAN_USE_GAMES, false))
                .canUseSurveys(getFeatureBool(code, FEAT_CAN_USE_SURVEYS, false))
                .maxProducts(getFeatureInt(code, FEAT_MAX_PRODUCTS, 0))
                .maxAds(getFeatureInt(code, FEAT_MAX_ADS, 0))
                .maxBrandedGames(getFeatureInt(code, FEAT_MAX_BRANDED_GAMES, 0))
                .maxSurveys(getFeatureInt(code, FEAT_MAX_SURVEYS, 0))
                .build();
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
}
