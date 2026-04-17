package com.verygana2.config;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.plans.Feature;
import com.verygana2.models.plans.Feature.FeatureType;
import com.verygana2.models.plans.Plan;
import com.verygana2.models.plans.Plan.PlanCode;
import com.verygana2.models.plans.PlanFeature;
import com.verygana2.repositories.plans.FeatureRepository;
import com.verygana2.repositories.plans.PlanFeatureRepository;
import com.verygana2.repositories.plans.PlanRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Inicializa el catálogo de planes y features al arrancar la aplicación.
 * Es idempotente: si los datos ya existen, no los duplica.
 *
 * Valores por defecto configurados:
 * ─────────────────────────────────────────────────────────────────
 *                     BASIC     STANDARD    PREMIUM
 * CAN_ADVERTISE       false     true        true
 * CAN_USE_GAMES       false     true        true
 * MAX_PRODUCTS        10        100         ilimitado (-1)
 * MAX_ADS             0         20          100
 * MAX_BRANDED_GAMES   0         5           20
 * SALES_COMMISSION    10%       5%          3%
 * VISIBILITY_BOOST    0%        20%         50%
 * ─────────────────────────────────────────────────────────────────
 *
 * Estos valores son configurables desde la BD sin necesidad de redeployar.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanDataInitializer implements ApplicationRunner {

    private final PlanRepository planRepository;
    private final FeatureRepository featureRepository;
    private final PlanFeatureRepository planFeatureRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (planRepository.count() > 0) {
            log.info("Planes ya inicializados. Omitiendo seed.");
            return;
        }

        log.info("Inicializando catálogo de planes y features...");

        // ── 1. Crear planes ───────────────────────────────────────────────────
        Plan basic = planRepository.save(Plan.builder()
                .code(PlanCode.BASIC)
                .version(1)
                .active(true)
                .name("Básico")
                .description("Plan de entrada con suscripción mensual y comisión por ventas. " +
                             "Sin anuncios ni juegos branded.")
                .monthlySubscription(true)
                .monthlyPrice(new BigDecimal("99000"))
                .minInvestment(null)
                .maxInvestment(null)
                .build());

        Plan standard = planRepository.save(Plan.builder()
                .code(PlanCode.STANDARD)
                .version(1)
                .active(true)
                .name("Estándar")
                .description("Activado con inversión entre $1.000.000 y $9.999.999. " +
                             "Incluye anuncios y juegos branded. No es suscripción recurrente.")
                .monthlySubscription(false)
                .monthlyPrice(null)
                .minInvestment(new BigDecimal("1000000"))
                .maxInvestment(new BigDecimal("9999999.99"))
                .build());

        Plan premium = planRepository.save(Plan.builder()
                .code(PlanCode.PREMIUM)
                .version(1)
                .active(true)
                .name("Premium")
                .description("Activado con inversión de $10.000.000 o más. " +
                             "Máxima visibilidad, comisión reducida. No es suscripción recurrente.")
                .monthlySubscription(false)
                .monthlyPrice(null)
                .minInvestment(new BigDecimal("10000000"))
                .maxInvestment(null)
                .build());

        // ── 2. Crear catálogo de features ─────────────────────────────────────
        Feature canAdvertise = featureRepository.save(Feature.builder()
                .code("CAN_ADVERTISE")
                .name("Puede publicar anuncios")
                .type(FeatureType.BOOLEAN)
                .build());

        Feature canUseGames = featureRepository.save(Feature.builder()
                .code("CAN_USE_GAMES")
                .name("Puede usar juegos branded")
                .type(FeatureType.BOOLEAN)
                .build());

        Feature maxProducts = featureRepository.save(Feature.builder()
                .code("MAX_PRODUCTS")
                .name("Máximo de productos activos")
                .type(FeatureType.LIMIT)
                .build());

        Feature maxAds = featureRepository.save(Feature.builder()
                .code("MAX_ADS")
                .name("Máximo de anuncios activos")
                .type(FeatureType.LIMIT)
                .build());

        Feature maxBrandedGames = featureRepository.save(Feature.builder()
                .code("MAX_BRANDED_GAMES")
                .name("Máximo de juegos branded activos")
                .type(FeatureType.LIMIT)
                .build());

        Feature salesCommission = featureRepository.save(Feature.builder()
                .code("SALES_COMMISSION")
                .name("Porcentaje de comisión por ventas")
                .type(FeatureType.PERCENTAGE)
                .build());

        Feature visibilityBoost = featureRepository.save(Feature.builder()
                .code("VISIBILITY_BOOST")
                .name("Boost de visibilidad en plataforma")
                .type(FeatureType.PERCENTAGE)
                .build());

        Feature canUseSurveys = featureRepository.save(Feature.builder()
                .code("CAN_USE_SURVEYS")
                .name("Puede usar encuestas")
                .type(FeatureType.BOOLEAN)
                .build());

        Feature maxSurveys = featureRepository.save(Feature.builder()
                .code("MAX_SURVEYS")
                .name("Máximo de encuestas activas")
                .type(FeatureType.LIMIT)
                .build());

        // ── 3. Asociar features a planes ──────────────────────────────────────
        List<PlanFeature> planFeatures = List.of(

            // BASIC
            pf(basic, canAdvertise,     null,  false,  null),
            pf(basic, canUseGames,      null,  false,  null),
            pf(basic, maxProducts,      10,    null,   null),
            pf(basic, maxAds,           0,     null,   null),
            pf(basic, maxBrandedGames,  0,     null,   null),
            pf(basic, salesCommission,  null,  null,   new BigDecimal("10.00")),
            pf(basic, visibilityBoost,  null,  null,   BigDecimal.ZERO),

            // STANDARD
            pf(standard, canAdvertise,     null,  true,   null),
            pf(standard, canUseGames,      null,  true,   null),
            pf(standard, maxProducts,      100,   null,   null),
            pf(standard, maxAds,           20,    null,   null),
            pf(standard, maxBrandedGames,  5,     null,   null),
            pf(standard, salesCommission,  null,  null,   new BigDecimal("5.00")),
            pf(standard, visibilityBoost,  null,  null,   new BigDecimal("20.00")),
            pf(standard, canUseSurveys,    null,  true,  null),
            pf(standard, maxSurveys,        10,    null,  null),


            // PREMIUM
            pf(premium, canAdvertise,     null,  true,   null),
            pf(premium, canUseGames,      null,  true,   null),
            pf(premium, maxProducts,      -1,    null,   null),   // -1 = ilimitado
            pf(premium, maxAds,           100,   null,   null),
            pf(premium, maxBrandedGames,  20,    null,   null),
            pf(premium, salesCommission,  null,  null,   new BigDecimal("3.00")),
            pf(premium, visibilityBoost,  null,  null,   new BigDecimal("50.00")),
            pf(premium, canUseSurveys,    null,  true,   null),
            pf(premium, maxSurveys,        50,    null,   null)
        );

        planFeatureRepository.saveAll(planFeatures);

        log.info("Catálogo inicializado: 3 planes, 7 features, {} asignaciones.",
                planFeatures.size());
    }

    private PlanFeature pf(Plan plan, Feature feature,
            Integer intVal, Boolean boolVal, BigDecimal decimalVal) {
        return PlanFeature.builder()
                .plan(plan)
                .feature(feature)
                .intValue(intVal)
                .boolValue(boolVal)
                .decimalValue(decimalVal)
                .build();
    }
}