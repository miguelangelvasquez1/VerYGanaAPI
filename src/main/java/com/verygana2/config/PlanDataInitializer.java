package com.verygana2.config;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.finance.plans.Feature;
import com.verygana2.models.finance.plans.Feature.FeatureType;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.finance.plans.PlanFeature;
import com.verygana2.repositories.finance.plans.FeatureRepository;
import com.verygana2.repositories.finance.plans.PlanFeatureRepository;
import com.verygana2.repositories.finance.plans.PlanRepository;

import lombok.RequiredArgsConstructor;

/**
 * Inicializa el catálogo de planes y features al arrancar la app.
 * Es IDEMPOTENTE — si los datos ya existen no hace nada.
 *
 * @Order(2) para correr después de TreasuryDataInitializer (@Order(1)).
 *
 * SALES_COMMISSION no está aquí — vive como campo directo en Plan.saleCommissionPct
 * porque se consulta en cada transacción financiera y un join sería costoso.
 * El resto de features son dinámicos y modificables por el admin vía endpoint.
 */
@Component
@RequiredArgsConstructor
@Order(2)
public class PlanDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlanDataInitializer.class);

    private final PlanRepository planRepository;
    private final FeatureRepository featureRepository;
    private final PlanFeatureRepository planFeatureRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (planRepository.count() > 0) {
            log.info("=== PlanDataInitializer: planes ya inicializados — omitiendo ===");
            return;
        }

        log.info("=== PlanDataInitializer: inicializando catálogo de planes ===");

        // ── 1. Crear planes ───────────────────────────────────────────────────
        Plan basic = planRepository.save(Plan.builder()
                .code(PlanCode.BASIC)
                .version(1)
                .active(true)
                .name("Básico")
                .description("Suscripción mensual fija. Ideal para empezar a vender " +
                             "productos digitales sin inversión publicitaria.")
                .monthlyPriceCents(20_000_000L)  // $200.000 COP
                .minInvestmentCents(null)
                .maxInvestmentCents(null)
                .saleCommissionPct(15)            // 15% por venta 
                .maxKeysPct(20) // 20% del precio de cada producto se puede pagar con llaves 
                .build());

        Plan standard = planRepository.save(Plan.builder()
                .code(PlanCode.STANDARD)
                .version(1)
                .active(true)
                .name("Estándar")
                .description("Depósito entre $1.000.000 y $9.999.999 COP. " +
                             "Incluye anuncios y juegos brandeados.")
                .monthlyPriceCents(null)
                .minInvestmentCents(100_000_000L)   // $1.000.000 COP
                .maxInvestmentCents(999_999_900L)   // $9.999.999 COP
                .saleCommissionPct(10)              // 10% por venta 
                .maxKeysPct(35) // 35% del precio de cada producto se puede pagar con llaves 
                .build());

        Plan premium = planRepository.save(Plan.builder()
                .code(PlanCode.PREMIUM)
                .version(1)
                .active(true)
                .name("Premium")
                .description("Depósito desde $10.000.000 COP. " +
                             "Acceso completo a todas las funcionalidades.")
                .monthlyPriceCents(null)
                .minInvestmentCents(1_000_000_000L) // $10.000.000 COP
                .maxInvestmentCents(null)            // sin techo
                .saleCommissionPct(5)              // 5% por venta 
                .maxKeysPct(50) // 50% del precio de cada producto se puede pagar con llaves 
                .build());

        // ── 2. Crear catálogo de features ─────────────────────────────────────
        // SALES_COMMISSION NO está aquí — vive en Plan.saleCommissionPct
        Feature canAdvertise = featureRepository.save(Feature.builder()
                .code("CAN_ADVERTISE")
                .name("Puede publicar anuncios")
                .type(FeatureType.BOOLEAN)
                .build());

        Feature canUseGames = featureRepository.save(Feature.builder()
                .code("CAN_USE_GAMES")
                .name("Puede usar juegos brandeados")
                .type(FeatureType.BOOLEAN)
                .build());

        Feature canUseSurveys = featureRepository.save(Feature.builder()
                .code("CAN_USE_SURVEYS")
                .name("Puede usar encuestas")
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
                .name("Máximo de juegos brandeados activos")
                .type(FeatureType.LIMIT)
                .build());

        Feature maxSurveys = featureRepository.save(Feature.builder()
                .code("MAX_SURVEYS")
                .name("Máximo de encuestas activas")
                .type(FeatureType.LIMIT)
                .build());

        Feature visibilityBoost = featureRepository.save(Feature.builder()
                .code("VISIBILITY_BOOST")
                .name("Boost de visibilidad en plataforma")
                .type(FeatureType.PERCENTAGE)
                .build());

        // ── 3. Asociar features a planes ──────────────────────────────────────
        List<PlanFeature> planFeatures = List.of(

            // ── BASIC ─────────────────────────────────────────────────────────
            pf(basic, canAdvertise,    null, false, null),
            pf(basic, canUseGames,     null, false, null),
            pf(basic, canUseSurveys,   null, false, null),
            pf(basic, maxProducts,     10,   null,  null),
            pf(basic, maxAds,          0,    null,  null),
            pf(basic, maxBrandedGames, 0,    null,  null),
            pf(basic, maxSurveys,      0,    null,  null),
            pf(basic, visibilityBoost, null, null,  BigDecimal.ZERO),

            // ── STANDARD ──────────────────────────────────────────────────────
            pf(standard, canAdvertise,    null, true,  null),
            pf(standard, canUseGames,     null, true,  null),
            pf(standard, canUseSurveys,   null, true,  null),
            pf(standard, maxProducts,     100,  null,  null),
            pf(standard, maxAds,          20,   null,  null),
            pf(standard, maxBrandedGames, 5,    null,  null),
            pf(standard, maxSurveys,      10,   null,  null),
            pf(standard, visibilityBoost, null, null,  new BigDecimal("20.00")),

            // ── PREMIUM ───────────────────────────────────────────────────────
            pf(premium, canAdvertise,    null, true,  null),
            pf(premium, canUseGames,     null, true,  null),
            pf(premium, canUseSurveys,   null, true,  null),
            pf(premium, maxProducts,     -1,   null,  null),  // -1 = ilimitado
            pf(premium, maxAds,          100,  null,  null),
            pf(premium, maxBrandedGames, 20,   null,  null),
            pf(premium, maxSurveys,      50,   null,  null),
            pf(premium, visibilityBoost, null, null,  new BigDecimal("50.00"))
        );

        planFeatureRepository.saveAll(planFeatures);

        log.info("=== PlanDataInitializer completado: 3 planes, {} feature-asignaciones ===",
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