package com.verygana2.models.raffles;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de la entidad {@link RaffleRule} (la configuración de una regla de
 * obtención de tickets aplicada a una rifa específica, con su propio límite
 * de fuente).
 */
@DisplayName("RaffleRule (entidad)")
class RaffleRuleTest {

    private TicketEarningRule activeGlobalRule() {
        TicketEarningRule rule = new TicketEarningRule();
        rule.setActive(true);
        return rule;
    }

    @Nested
    @DisplayName("canIssueTickets")
    class CanIssueTickets {

        @Test
        @DisplayName("config inactiva: no puede emitir aunque la regla global esté activa")
        void inactiveConfig_cannotIssue() {
            RaffleRule rule = new RaffleRule();
            rule.setActive(false);
            rule.setTicketEarningRule(activeGlobalRule());

            assertThat(rule.canIssueTickets(1)).isFalse();
        }

        @Test
        @DisplayName("regla global inactiva: no puede emitir aunque la config esté activa")
        void inactiveGlobalRule_cannotIssue() {
            RaffleRule rule = new RaffleRule();
            rule.setActive(true);
            TicketEarningRule global = new TicketEarningRule();
            global.setActive(false);
            rule.setTicketEarningRule(global);

            assertThat(rule.canIssueTickets(1)).isFalse();
        }

        @Test
        @DisplayName("sin límite de fuente configurado: siempre puede emitir")
        void noSourceLimit_alwaysCanIssue() {
            RaffleRule rule = new RaffleRule();
            rule.setActive(true);
            rule.setTicketEarningRule(activeGlobalRule());
            rule.setMaxTicketsBySource(null);

            assertThat(rule.canIssueTickets(1_000_000)).isTrue();
        }

        @Test
        @DisplayName("con límite: puede emitir mientras no lo supere, y no puede al superarlo")
        void withSourceLimit_respectsLimit() {
            RaffleRule rule = new RaffleRule();
            rule.setActive(true);
            rule.setTicketEarningRule(activeGlobalRule());
            rule.setMaxTicketsBySource(100L);
            rule.setCurrentTicketsBySource(95L);

            assertThat(rule.canIssueTickets(5)).isTrue(); // 95 + 5 = 100, justo al límite
            assertThat(rule.canIssueTickets(6)).isFalse(); // 95 + 6 = 101, se pasa
        }
    }

    @Test
    @DisplayName("getRemainingTickets: null si no hay límite, o el restante (nunca negativo) si lo hay")
    void getRemainingTickets_computesCorrectly() {
        RaffleRule unlimited = new RaffleRule();
        unlimited.setMaxTicketsBySource(null);
        assertThat(unlimited.getRemainingTickets()).isNull();

        RaffleRule limited = new RaffleRule();
        limited.setMaxTicketsBySource(100L);
        limited.setCurrentTicketsBySource(80L);
        assertThat(limited.getRemainingTickets()).isEqualTo(20L);

        RaffleRule exceeded = new RaffleRule();
        exceeded.setMaxTicketsBySource(100L);
        exceeded.setCurrentTicketsBySource(150L); // no debería pasar en la práctica, pero no debe dar negativo
        assertThat(exceeded.getRemainingTickets()).isZero();
    }

    @Test
    @DisplayName("incrementIssuedCount: suma la cantidad indicada")
    void incrementIssuedCount_addsQuantity() {
        RaffleRule rule = new RaffleRule();
        rule.setCurrentTicketsBySource(10L);

        rule.incrementIssuedCount(5);

        assertThat(rule.getCurrentTicketsBySource()).isEqualTo(15L);
    }

    @Test
    @DisplayName("incrementIssuedCount: cantidad no positiva lanza IllegalArgumentException")
    void incrementIssuedCount_nonPositiveQuantity_throws() {
        RaffleRule rule = new RaffleRule();
        rule.setCurrentTicketsBySource(0L);

        assertThatThrownBy(() -> rule.incrementIssuedCount(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
