package com.verygana2.repositories.finance.plans;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.finance.plans.PlanFeature;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para PlanFeatureRepository.
 *
 * findByPlanCodeAndFeatureCode(PlanCode, String) navega implícitamente
 * plan.code (no un planId), ya que PlanFeature no tiene una propiedad
 * "planCode" propia: Spring Data resuelve "PlanCode" como plan.code y
 * "FeatureCode" como feature.code.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:plan-feature-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PlanFeatureRepository (integración H2)")
class PlanFeatureRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private PlanFeatureRepository planFeatureRepository;

    // Plan.code es unique: un solo plan por PlanCode en toda la tabla.
    private final AtomicInteger versionSeq = new AtomicInteger(1);

    // ==================== HELPERS ====================

    private Plan persistPlan(PlanCode code) {
        Plan plan = new Plan();
        plan.setVersion(versionSeq.getAndIncrement());
        plan.setActive(true);
        plan.setCode(code);
        plan.setName(code.name() + " Test");
        plan.setSaleCommissionPct(10);
        plan.setMaxKeysPct(20);
        em.persist(plan);
        em.flush();
        return plan;
    }

    // ==================== findByPlanCodeAndFeatureCode ====================

    @Nested
    @DisplayName("findByPlanCodeAndFeatureCode")
    class FindByPlanCodeAndFeatureCode {

        @Test
        @DisplayName("trae la PlanFeature del plan correcto cuando dos planes comparten el mismo featureCode")
        void bringsFeatureFromCorrectPlan() {
            Plan standard = persistPlan(PlanCode.STANDARD);
            Plan premium = persistPlan(PlanCode.PREMIUM);

            TestEntities.persistIntPlanFeature(em, standard, "MAX_PRODUCTS", 10);
            TestEntities.persistIntPlanFeature(em, premium, "MAX_PRODUCTS", 50);

            Optional<PlanFeature> foundStandard = planFeatureRepository
                    .findByPlanCodeAndFeatureCode(PlanCode.STANDARD, "MAX_PRODUCTS");
            Optional<PlanFeature> foundPremium = planFeatureRepository
                    .findByPlanCodeAndFeatureCode(PlanCode.PREMIUM, "MAX_PRODUCTS");

            assertThat(foundStandard).isPresent();
            assertThat(foundStandard.get().getIntValue()).isEqualTo(10);

            assertThat(foundPremium).isPresent();
            assertThat(foundPremium.get().getIntValue()).isEqualTo(50);
        }

        @Test
        @DisplayName("retorna vacío si el plan no tiene configurado ese featureCode")
        void returnsEmptyWhenFeatureCodeNotConfiguredForPlan() {
            Plan standard = persistPlan(PlanCode.STANDARD);
            TestEntities.persistIntPlanFeature(em, standard, "MAX_PRODUCTS", 10);

            Optional<PlanFeature> found = planFeatureRepository
                    .findByPlanCodeAndFeatureCode(PlanCode.STANDARD, "MAX_ADS");

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("retorna vacío si el plan no existe")
        void returnsEmptyWhenPlanDoesNotExist() {
            Optional<PlanFeature> found = planFeatureRepository
                    .findByPlanCodeAndFeatureCode(PlanCode.BASIC, "MAX_PRODUCTS");

            assertThat(found).isEmpty();
        }
    }
}
