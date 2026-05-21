package com.verygana2.services.raffles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.RaffleRule;
import com.verygana2.repositories.raffles.RaffleRuleRespository;

@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleRuleServiceImpl")
class RaffleRuleServiceImplTest {

    @Mock RaffleRuleRespository raffleRuleRespository;

    @InjectMocks RaffleRuleServiceImpl service;

    // ─── getByRaffleIdAndRuleType ─────────────────────────────────────────────

    @Nested
    @DisplayName("getByRaffleIdAndRuleType")
    class GetByRaffleIdAndRuleType {

        @Test
        @DisplayName("throws IllegalArgumentException for null raffle ID")
        void throwsOnNullId() {
            assertThatThrownBy(() -> service.getByRaffleIdAndRuleType(null, TicketEarningRuleType.PURCHASE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for zero raffle ID")
        void throwsOnZeroId() {
            assertThatThrownBy(() -> service.getByRaffleIdAndRuleType(0L, TicketEarningRuleType.PURCHASE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for negative raffle ID")
        void throwsOnNegativeId() {
            assertThatThrownBy(() -> service.getByRaffleIdAndRuleType(-1L, TicketEarningRuleType.PURCHASE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("returns RaffleRule when found")
        void returnsRule() {
            RaffleRule rule = new RaffleRule();
            rule.setId(1L);
            when(raffleRuleRespository.findByRaffleIdAndRuleType(1L, TicketEarningRuleType.PURCHASE))
                    .thenReturn(Optional.of(rule));

            RaffleRule result = service.getByRaffleIdAndRuleType(1L, TicketEarningRuleType.PURCHASE);

            assertThat(result).isSameAs(rule);
        }

        @Test
        @DisplayName("throws ObjectNotFoundException when no rule found")
        void throwsWhenNotFound() {
            when(raffleRuleRespository.findByRaffleIdAndRuleType(1L, TicketEarningRuleType.PURCHASE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByRaffleIdAndRuleType(1L, TicketEarningRuleType.PURCHASE))
                    .isInstanceOf(ObjectNotFoundException.class);
        }
    }
}
