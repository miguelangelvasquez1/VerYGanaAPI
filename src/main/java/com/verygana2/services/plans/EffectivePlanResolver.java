package com.verygana2.services.plans;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.plans.EffectivePlanState;
import com.verygana2.models.plans.Investment;
import com.verygana2.models.plans.Plan;
import com.verygana2.models.plans.Plan.PlanCode;
import com.verygana2.models.plans.Subscription;
import com.verygana2.repositories.plans.InvestmentRepository;
import com.verygana2.repositories.plans.PlanFeatureRepository;
import com.verygana2.repositories.plans.SubscriptionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio central que resuelve el estado efectivo de un anunciante.
 *
 * Es el único punto de verdad para responder:
 * "¿Qué puede hacer este anunciante ahora mismo?"
 *
 * Algoritmo de resolución:
 * ─────────────────────────────────────────────────────────────────
 * 1. Buscar el Budget activo del anunciante.
 *    → Si no existe o remainingAmount == 0: modo BASIC.
 *
 * 2. Si hay budget activo:
 *    → Determinar el plan por investmentAmount:
 *        [1_000_000 – 9_999_999)  → STANDARD
 *        [10_000_000 – ∞)         → PREMIUM
 *
 * 3. Verificar ROI:
 *    → Si investment.roiReached == true:
 *        activar comisión aunque el budget esté activo.
 *
 * 4. Resolver features del plan efectivo (PlanFeature).
 * ─────────────────────────────────────────────────────────────────
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EffectivePlanResolver {

    private static final BigDecimal STANDARD_MIN = new BigDecimal("1000000");
    private static final BigDecimal PREMIUM_MIN  = new BigDecimal("10000000");

    // Códigos de feature — deben coincidir con Feature.code en BD
    private static final String FEAT_CAN_ADVERTISE     = "CAN_ADVERTISE";
    private static final String FEAT_CAN_USE_GAMES     = "CAN_USE_GAMES";
    private static final String FEAT_MAX_PRODUCTS      = "MAX_PRODUCTS";
    private static final String FEAT_MAX_ADS           = "MAX_ADS";
    private static final String FEAT_MAX_BRANDED_GAMES = "MAX_BRANDED_GAMES";
    private static final String FEAT_SALES_COMMISSION  = "SALES_COMMISSION";

    private final InvestmentRepository              investmentRepository;
    private final PlanFeatureRepository         planFeatureRepository;
    private final SubscriptionRepository      subscriptionRepository;

    /**
     * Resuelve el estado efectivo del anunciante en tiempo real.
     *
     * @param commercialId ID del CommercialDetails del anunciante
     * @return Estado efectivo con todas las capacidades y flags resueltos
     */
    @Transactional(readOnly = true)
    public EffectivePlanState resolve(Long commercialId) {

        // ── 1. Buscar ciclo activo (STANDARD / PREMIUM) ─────────────────────────
        Optional<Investment> activeCycleOpt =
                investmentRepository.findActiveByCommercialId(commercialId);

        if (activeCycleOpt.isPresent()) {
            Investment cycle = activeCycleOpt.get();

            if (cycle.hasFunds()) {
                Plan plan = cycle.getPlan();
                boolean commissionActive = cycle.isRoiReached();

                log.debug("Advertiser {} → ACTIVE PLAN (cycle) plan={}, commissionActive={}, balance={}",
                        commercialId,
                        plan.getCode(),
                        commissionActive,
                        cycle.getRemainingAmount());

                return buildStateForPlan(
                        plan.getCode(),
                        commissionActive,
                        cycle.getRemainingAmount(),
                        cycle.isRoiReached()
                );
            }
        }

        // ── 2. No hay ciclo activo → buscar suscripción BASIC ───────────────────
        Optional<Subscription> activeSubscriptionOpt =
                subscriptionRepository.findActiveByCommercialId(commercialId);

        if (activeSubscriptionOpt.isPresent()) {
            Subscription subscription = activeSubscriptionOpt.get();

            if (subscription.isActive()) {
                log.debug("Advertiser {} → BASIC (subscription active)", commercialId);

                return buildBasicState();
            }
        }

        // ── 3. No tiene ciclo ni suscripción → SIN PLAN ─────────────────────────
        log.debug("Advertiser {} → NO PLAN (no investment, no subscription)", commercialId);

        return buildNoPlanState();
    }

    /**
     * Determina qué plan corresponde según el monto de inversión.
     */
    public PlanCode resolvePlanByInvestment(BigDecimal investmentAmount) {
        if (investmentAmount.compareTo(PREMIUM_MIN) >= 0) {
            return PlanCode.PREMIUM;
        } else if (investmentAmount.compareTo(STANDARD_MIN) >= 0) {
            return PlanCode.STANDARD;
        }
        // Inversión menor al mínimo de STANDARD → comportamiento BASIC
        return PlanCode.BASIC;
    }

    // ── Builders de estado ────────────────────────────────────────────────────

    private EffectivePlanState buildNoPlanState() {
        return EffectivePlanState.noPlanMode();
    }

    private EffectivePlanState buildBasicState() {
        BigDecimal commission = getFeatureDecimal(PlanCode.BASIC, FEAT_SALES_COMMISSION,
                new BigDecimal("10.00"));
        int maxProducts = getFeatureInt(PlanCode.BASIC, FEAT_MAX_PRODUCTS, 10);

        return EffectivePlanState.basicMode(commission, maxProducts);
    }

    private EffectivePlanState buildStateForPlan(PlanCode planCode, boolean commissionActive,
            BigDecimal remainingBudget, boolean roiReached) {

        BigDecimal commissionRate = commissionActive
                ? getFeatureDecimal(planCode, FEAT_SALES_COMMISSION, BigDecimal.ZERO)
                : BigDecimal.ZERO;

        return EffectivePlanState.builder()
                .hasActivePlan(true)
                .effectivePlan(planCode)
                .commissionActive(commissionActive)
                .commissionRate(commissionRate)
                .remainingBudget(remainingBudget)
                .canAdvertise(getFeatureBool(planCode, FEAT_CAN_ADVERTISE, false))
                .canUseGames(getFeatureBool(planCode, FEAT_CAN_USE_GAMES, false))
                .maxProducts(getFeatureInt(planCode, FEAT_MAX_PRODUCTS, 10))
                .maxAds(getFeatureInt(planCode, FEAT_MAX_ADS, 0))
                .maxBrandedGames(getFeatureInt(planCode, FEAT_MAX_BRANDED_GAMES, 0))
                .roiReached(roiReached)
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

    private BigDecimal getFeatureDecimal(PlanCode planCode, String featureCode, BigDecimal defaultVal) {
        return planFeatureRepository.findByPlanCodeAndFeatureCode(planCode, featureCode)
                .map(pf -> pf.getDecimalOrDefault(defaultVal))
                .orElse(defaultVal);
    }
}