package com.verygana2.models.raffles;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.models.enums.raffles.RaffleStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de la entidad {@link Raffle}: el límite total de tickets y los
 * contadores que se incrementan en cada emisión.
 */
@DisplayName("Raffle (entidad)")
class RaffleTest {

    @Test
    @DisplayName("hasReachedTotalLimit: sin límite configurado, nunca se considera alcanzado")
    void hasReachedTotalLimit_noLimitConfigured_neverReached() {
        Raffle raffle = new Raffle();
        raffle.setMaxTotalTickets(null);
        raffle.setTotalTicketsIssued(999_999);

        assertThat(raffle.hasReachedTotalLimit()).isFalse();
    }

    @Test
    @DisplayName("hasReachedTotalLimit: true cuando los emitidos igualan o superan el máximo")
    void hasReachedTotalLimit_trueWhenIssuedReachesMax() {
        Raffle raffle = new Raffle();
        raffle.setMaxTotalTickets(100);
        raffle.setTotalTicketsIssued(100);

        assertThat(raffle.hasReachedTotalLimit()).isTrue();
    }

    @Test
    @DisplayName("incrementTicketCount: suma la cantidad indicada al contador")
    void incrementTicketCount_addsQuantity() {
        Raffle raffle = new Raffle();
        raffle.setTotalTicketsIssued(10);

        raffle.incrementTicketCount(5);

        assertThat(raffle.getTotalTicketsIssued()).isEqualTo(15);
    }

    @Test
    @DisplayName("incrementTicketCount: cantidad no positiva lanza IllegalArgumentException")
    void incrementTicketCount_nonPositiveQuantity_throws() {
        Raffle raffle = new Raffle();
        raffle.setTotalTicketsIssued(0);

        assertThatThrownBy(() -> raffle.incrementTicketCount(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("incrementParticipantCount: suma 1 al contador de participantes")
    void incrementParticipantCount_addsOne() {
        Raffle raffle = new Raffle();
        raffle.setTotalParticipants(3);

        raffle.incrementParticipantCount();

        assertThat(raffle.getTotalParticipants()).isEqualTo(4);
    }

    @Test
    @DisplayName("onCreate (hook @PrePersist): nace en DRAFT con contadores en cero")
    void onCreate_defaultsToDraftWithZeroCounters() {
        Raffle raffle = new Raffle();

        raffle.onCreate();

        assertThat(raffle.getRaffleStatus()).isEqualTo(RaffleStatus.DRAFT);
        assertThat(raffle.getTotalTicketsIssued()).isZero();
        assertThat(raffle.getTotalParticipants()).isZero();
        assertThat(raffle.getCreatedAt()).isNotNull();
    }
}
