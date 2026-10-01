package com.verygana2.repositories.finance.plans;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
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

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para PlanRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:plan-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PlanRepository (integración H2)")
class PlanRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private PlanRepository planRepository;

    // Plan.code tiene @Column(unique = true): solo puede existir UN plan por
    // PlanCode en toda la tabla. version se incrementa para no chocar con el
    // uniqueConstraint(version, code) al reutilizar el mismo code entre tests.
    private final AtomicInteger versionSeq = new AtomicInteger(1);

    // ==================== HELPERS ====================

    private Plan persistPlan(PlanCode code, boolean active, Long minInvestmentCents, Long maxInvestmentCents) {
        Plan plan = new Plan();
        plan.setVersion(versionSeq.getAndIncrement());
        plan.setActive(active);
        plan.setCode(code);
        plan.setName(code.name() + " Test");
        plan.setSaleCommissionPct(10);
        plan.setMaxKeysPct(20);
        plan.setMinInvestmentCents(minInvestmentCents);
        plan.setMaxInvestmentCents(maxInvestmentCents);
        em.persist(plan);
        em.flush();
        return plan;
    }

    // ==================== findByCodeAndActiveTrue ====================

    @Nested
    @DisplayName("findByCodeAndActiveTrue")
    class FindByCodeAndActiveTrue {

        @Test
        @DisplayName("retorna el plan activo con el code buscado")
        void returnsActivePlanWithMatchingCode() {
            Plan standard = persistPlan(PlanCode.STANDARD, true, 100_000_000L, 999_999_900L);

            Optional<Plan> found = planRepository.findByCodeAndActiveTrue(PlanCode.STANDARD);

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(standard.getId());
        }

        @Test
        @DisplayName("retorna vacío si el plan con ese code está inactivo")
        void returnsEmptyWhenPlanIsInactive() {
            persistPlan(PlanCode.STANDARD, false, 100_000_000L, 999_999_900L);

            Optional<Plan> found = planRepository.findByCodeAndActiveTrue(PlanCode.STANDARD);

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("retorna vacío si no existe plan con ese code")
        void returnsEmptyWhenPlanDoesNotExist() {
            Optional<Plan> found = planRepository.findByCodeAndActiveTrue(PlanCode.PREMIUM);

            assertThat(found).isEmpty();
        }
    }

    // ==================== findAllByActiveTrue ====================

    @Nested
    @DisplayName("findAllByActiveTrue")
    class FindAllByActiveTrue {

        @Test
        @DisplayName("retorna solo los planes activos")
        void returnsOnlyActivePlans() {
            Plan standard = persistPlan(PlanCode.STANDARD, true, 100_000_000L, 999_999_900L);
            persistPlan(PlanCode.PREMIUM, false, 1_000_000_000L, null);

            List<Plan> found = planRepository.findAllByActiveTrue();

            assertThat(found).extracting(Plan::getId).containsExactly(standard.getId());
        }

        @Test
        @DisplayName("retorna lista vacía si no hay planes activos")
        void returnsEmptyListWhenNoActivePlans() {
            persistPlan(PlanCode.STANDARD, false, 100_000_000L, 999_999_900L);

            List<Plan> found = planRepository.findAllByActiveTrue();

            assertThat(found).isEmpty();
        }
    }

    // ==================== findEligiblePlans ====================

    @Nested
    @DisplayName("findEligiblePlans")
    class FindEligiblePlans {

        @Test
        @DisplayName("incluye el plan cuando amount es exactamente igual al mínimo de inversión")
        void includesPlanWhenAmountEqualsMin() {
            Plan standard = persistPlan(PlanCode.STANDARD, true, 100_000_000L, 999_999_900L);

            List<Plan> found = planRepository.findEligiblePlans(BigDecimal.valueOf(100_000_000L));

            assertThat(found).extracting(Plan::getId).containsExactly(standard.getId());
        }

        @Test
        @DisplayName("incluye el plan cuando amount es exactamente igual al máximo de inversión")
        void includesPlanWhenAmountEqualsMax() {
            Plan standard = persistPlan(PlanCode.STANDARD, true, 100_000_000L, 999_999_900L);

            List<Plan> found = planRepository.findEligiblePlans(BigDecimal.valueOf(999_999_900L));

            assertThat(found).extracting(Plan::getId).containsExactly(standard.getId());
        }

        @Test
        @DisplayName("excluye el plan cuando amount supera el máximo de inversión")
        void excludesPlanWhenAmountExceedsMax() {
            persistPlan(PlanCode.STANDARD, true, 100_000_000L, 999_999_900L);

            List<Plan> found = planRepository.findEligiblePlans(BigDecimal.valueOf(1_000_000_000L));

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("excluye el plan cuando amount es menor al mínimo de inversión")
        void excludesPlanWhenAmountBelowMin() {
            persistPlan(PlanCode.STANDARD, true, 100_000_000L, 999_999_900L);

            List<Plan> found = planRepository.findEligiblePlans(BigDecimal.valueOf(99_999_999L));

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("plan PREMIUM con maxInvestmentCents null no tiene límite superior")
        void premiumWithNullMaxHasNoUpperBound() {
            Plan premium = persistPlan(PlanCode.PREMIUM, true, 1_000_000_000L, null);

            List<Plan> found = planRepository.findEligiblePlans(BigDecimal.valueOf(50_000_000_000L));

            assertThat(found).extracting(Plan::getId).containsExactly(premium.getId());
        }

        @Test
        @DisplayName("excluye siempre los planes BASIC sin importar el monto")
        void alwaysExcludesBasicPlans() {
            persistPlan(PlanCode.BASIC, true, null, null);

            List<Plan> found = planRepository.findEligiblePlans(BigDecimal.valueOf(1L));

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("excluye los planes inactivos aunque el monto encaje en su rango")
        void excludesInactivePlans() {
            persistPlan(PlanCode.STANDARD, false, 100_000_000L, 999_999_900L);

            List<Plan> found = planRepository.findEligiblePlans(BigDecimal.valueOf(500_000_000L));

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("ordena los planes elegibles descendentemente por minInvestmentCents")
        void ordersDescendingByMinInvestmentCents() {
            // Rangos artificiales (no reflejan los valores reales de negocio) solo
            // para forzar que ambos planes sean elegibles para el mismo amount y
            // así poder verificar el ORDER BY.
            Plan standard = persistPlan(PlanCode.STANDARD, true, 300_000_000L, 2_000_000_000L);
            Plan premium = persistPlan(PlanCode.PREMIUM, true, 100_000_000L, null);

            List<Plan> found = planRepository.findEligiblePlans(BigDecimal.valueOf(500_000_000L));

            assertThat(found).extracting(Plan::getId)
                    .containsExactly(standard.getId(), premium.getId());
        }
    }
}
