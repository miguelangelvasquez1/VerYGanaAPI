package com.verygana2.repositories.raffles;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.TicketEarningRule;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para TicketEarningRuleRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:ticket-earning-rule-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("TicketEarningRuleRepository (integración H2)")
class TicketEarningRuleRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private TicketEarningRuleRepository ticketEarningRuleRepository;

    // ==================== HELPERS ====================

    /**
     * Persiste una regla. onCreate() fuerza isActive=true al persistir por
     * primera vez, así que si se pide inactiva se hace un segundo flush
     * actualizándola, igual que con Raffle.raffleStatus.
     */
    private TicketEarningRule persistRule(String ruleName, TicketEarningRuleType type, int priority, boolean active) {
        TicketEarningRule rule = new TicketEarningRule();
        rule.setRuleName(ruleName);
        rule.setRuleType(type);
        rule.setPriority(priority);
        rule.setTicketsToAward(1);
        em.persist(rule);
        em.flush();

        if (!active) {
            rule.setActive(false);
            em.flush();
        }
        return rule;
    }

    // ==================== findByRuleTypeAndIsActiveTrueOrderByPriorityDesc ====================

    @Nested
    @DisplayName("findByRuleTypeAndIsActiveTrueOrderByPriorityDesc")
    class FindByRuleTypeAndIsActiveTrueOrderByPriorityDesc {

        @Test
        @DisplayName("trae solo reglas activas del tipo pedido, ordenadas por prioridad descendente")
        void returnsActiveRulesOfTypeOrderedByPriorityDesc() {
            TicketEarningRule lowPriority = persistRule("Compra baja prioridad", TicketEarningRuleType.PURCHASE, 1, true);
            TicketEarningRule highPriority = persistRule("Compra alta prioridad", TicketEarningRuleType.PURCHASE, 10, true);
            persistRule("Compra inactiva", TicketEarningRuleType.PURCHASE, 5, false);
            persistRule("Referido activo", TicketEarningRuleType.REFERRAL, 20, true);

            List<TicketEarningRule> result = ticketEarningRuleRepository
                    .findByRuleTypeAndIsActiveTrueOrderByPriorityDesc(TicketEarningRuleType.PURCHASE);

            assertThat(result).extracting(TicketEarningRule::getId)
                    .containsExactly(highPriority.getId(), lowPriority.getId());
        }
    }

    // ==================== findByRuleTypeAndIsActiveOrderByPriorityDesc ====================

    @Nested
    @DisplayName("findByRuleTypeAndIsActiveOrderByPriorityDesc")
    class FindByRuleTypeAndIsActiveOrderByPriorityDesc {

        @Test
        @DisplayName("sin filtros (ambos null) trae todas las reglas ordenadas por prioridad")
        void bothFiltersNullReturnsAllOrderedByPriority() {
            TicketEarningRule low = persistRule("Regla low", TicketEarningRuleType.PURCHASE, 1, true);
            TicketEarningRule high = persistRule("Regla high", TicketEarningRuleType.REFERRAL, 10, false);

            List<TicketEarningRule> result = ticketEarningRuleRepository
                    .findByRuleTypeAndIsActiveOrderByPriorityDesc(null, null, PageRequest.of(0, 10));

            assertThat(result).extracting(TicketEarningRule::getId)
                    .containsExactly(high.getId(), low.getId());
        }

        @Test
        @DisplayName("filtra solo por ruleType cuando isActive es null")
        void filtersOnlyByRuleTypeWhenIsActiveNull() {
            persistRule("Compra inactiva 2", TicketEarningRuleType.PURCHASE, 3, false);
            persistRule("Login activo", TicketEarningRuleType.DAILY_LOGIN, 8, true);

            List<TicketEarningRule> result = ticketEarningRuleRepository
                    .findByRuleTypeAndIsActiveOrderByPriorityDesc(TicketEarningRuleType.PURCHASE, null, PageRequest.of(0, 10));

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(r -> r.getRuleType() == TicketEarningRuleType.PURCHASE);
        }

        @Test
        @DisplayName("filtra solo por isActive cuando ruleType es null")
        void filtersOnlyByIsActiveWhenRuleTypeNull() {
            persistRule("Regla activa 1", TicketEarningRuleType.PURCHASE, 1, true);
            persistRule("Regla activa 2", TicketEarningRuleType.REFERRAL, 2, true);
            persistRule("Regla inactiva 1", TicketEarningRuleType.DAILY_LOGIN, 3, false);

            List<TicketEarningRule> activeOnly = ticketEarningRuleRepository
                    .findByRuleTypeAndIsActiveOrderByPriorityDesc(null, true, PageRequest.of(0, 10));
            List<TicketEarningRule> inactiveOnly = ticketEarningRuleRepository
                    .findByRuleTypeAndIsActiveOrderByPriorityDesc(null, false, PageRequest.of(0, 10));

            assertThat(activeOnly).hasSize(2);
            assertThat(inactiveOnly).hasSize(1);
        }

        @Test
        @DisplayName("filtra por ambos criterios combinados cuando se especifican los dos")
        void filtersByBothCriteriaCombined() {
            TicketEarningRule match = persistRule("Match exacto", TicketEarningRuleType.PURCHASE, 7, true);
            persistRule("Mismo tipo inactivo", TicketEarningRuleType.PURCHASE, 4, false);
            persistRule("Mismo estado otro tipo", TicketEarningRuleType.REFERRAL, 9, true);

            List<TicketEarningRule> result = ticketEarningRuleRepository
                    .findByRuleTypeAndIsActiveOrderByPriorityDesc(TicketEarningRuleType.PURCHASE, true, PageRequest.of(0, 10));

            assertThat(result).extracting(TicketEarningRule::getId).containsExactly(match.getId());
        }
    }

    // ==================== existsByRuleName ====================

    @Nested
    @DisplayName("existsByRuleName")
    class ExistsByRuleName {

        @Test
        @DisplayName("detecta si ya existe una regla con ese nombre")
        void detectsExistingRuleName() {
            persistRule("Nombre único de prueba", TicketEarningRuleType.PURCHASE, 1, true);

            assertThat(ticketEarningRuleRepository.existsByRuleName("Nombre único de prueba")).isTrue();
            assertThat(ticketEarningRuleRepository.existsByRuleName("Nombre que no existe")).isFalse();
        }
    }

    // ==================== countByIsActiveTrue ====================

    @Nested
    @DisplayName("countByIsActiveTrue")
    class CountByIsActiveTrue {

        @Test
        @DisplayName("cuenta únicamente las reglas activas")
        void countsOnlyActiveRules() {
            persistRule("Activa A", TicketEarningRuleType.PURCHASE, 1, true);
            persistRule("Activa B", TicketEarningRuleType.REFERRAL, 2, true);
            persistRule("Inactiva A", TicketEarningRuleType.DAILY_LOGIN, 3, false);

            assertThat(ticketEarningRuleRepository.countByIsActiveTrue()).isEqualTo(2);
        }
    }
}
