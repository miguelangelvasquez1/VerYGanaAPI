package com.verygana2.config;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
 * Garantiza que las features de métricas del comercial existan, incluso en bases
 * de datos que ya fueron sembradas por {@link PlanDataInitializer} antes de que
 * estas features existieran (ese seed solo corre cuando la tabla de planes está
 * vacía).
 *
 * IDEMPOTENTE: verifica cada Feature por código y cada PlanFeature por
 * (plan, feature); solo inserta lo que falta. Seguro en cada arranque.
 *
 * <ul>
 *   <li>{@code CAN_VIEW_PERFORMANCE_METRICS} → BASIC=false, STANDARD=true, PREMIUM=true</li>
 *   <li>{@code CAN_VIEW_PAGE_VISIT_METRICS}  → BASIC=false, STANDARD=false, PREMIUM=true</li>
 * </ul>
 *
 * @Order(3) para correr después de {@link PlanDataInitializer} (@Order(2)).
 */
@Component
@RequiredArgsConstructor
@Order(3)
public class MetricsReportFeatureInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MetricsReportFeatureInitializer.class);

    private static final String FEAT_PERFORMANCE = "CAN_VIEW_PERFORMANCE_METRICS";
    private static final String FEAT_PAGE_VISITS = "CAN_VIEW_PAGE_VISIT_METRICS";

    private final PlanRepository planRepository;
    private final FeatureRepository featureRepository;
    private final PlanFeatureRepository planFeatureRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (planRepository.count() == 0) {
            // BD nueva: PlanDataInitializer ya siembra estas features.
            return;
        }

        Feature performance = ensureFeature(FEAT_PERFORMANCE,
                "Puede ver métricas de rendimiento de anuncios, encuestas y campañas");
        Feature pageVisits = ensureFeature(FEAT_PAGE_VISITS,
                "Puede ver la métrica de visitas a su página oficial (Remisión)");

        Map<PlanCode, Boolean> performanceByPlan = new EnumMap<>(PlanCode.class);
        performanceByPlan.put(PlanCode.BASIC, false);
        performanceByPlan.put(PlanCode.STANDARD, true);
        performanceByPlan.put(PlanCode.PREMIUM, true);

        Map<PlanCode, Boolean> pageVisitsByPlan = new EnumMap<>(PlanCode.class);
        pageVisitsByPlan.put(PlanCode.BASIC, false);
        pageVisitsByPlan.put(PlanCode.STANDARD, false);
        pageVisitsByPlan.put(PlanCode.PREMIUM, true);

        int created = 0;
        for (PlanCode code : PlanCode.values()) {
            created += ensurePlanFeature(code, performance, performanceByPlan.get(code));
            created += ensurePlanFeature(code, pageVisits, pageVisitsByPlan.get(code));
        }

        if (created > 0) {
            log.info("=== MetricsReportFeatureInitializer: {} feature-asignaciones creadas ===", created);
        }
    }

    private Feature ensureFeature(String code, String name) {
        return featureRepository.findByCode(code)
                .orElseGet(() -> featureRepository.save(Feature.builder()
                        .code(code)
                        .name(name)
                        .type(FeatureType.BOOLEAN)
                        .build()));
    }

    /** @return 1 si insertó la fila, 0 si ya existía o el plan no está activo. */
    private int ensurePlanFeature(PlanCode planCode, Feature feature, boolean value) {
        if (planFeatureRepository.findByPlanCodeAndFeatureCode(planCode, feature.getCode()).isPresent()) {
            return 0;
        }
        List<Plan> plans = planRepository.findAllByActiveTrue();
        Plan plan = plans.stream().filter(p -> p.getCode() == planCode).findFirst().orElse(null);
        if (plan == null) {
            return 0;
        }
        planFeatureRepository.save(PlanFeature.builder()
                .plan(plan)
                .feature(feature)
                .boolValue(value)
                .build());
        return 1;
    }
}
