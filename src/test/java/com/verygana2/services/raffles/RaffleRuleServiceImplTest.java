package com.verygana2.services.raffles;

import java.util.Optional;

import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.RaffleRule;
import com.verygana2.repositories.raffles.RaffleRuleRespository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link RaffleRuleServiceImpl}: busca la configuración de una
 * regla de obtención de tickets para una rifa concreta.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleRuleServiceImpl")
class RaffleRuleServiceImplTest {

    @Mock private RaffleRuleRespository raffleRuleRespository;

    private RaffleRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RaffleRuleServiceImpl(raffleRuleRespository);
    }

    @Test
    @DisplayName("regla encontrada: la retorna")
    void found_returnsRule() {
        RaffleRule rule = new RaffleRule();
        when(raffleRuleRespository.findByRaffleIdAndRuleType(1L, TicketEarningRuleType.PURCHASE))
                .thenReturn(Optional.of(rule));

        assertThat(service.getByRaffleIdAndRuleType(1L, TicketEarningRuleType.PURCHASE)).isSameAs(rule);
    }

    @Test
    @DisplayName("regla no configurada para esa rifa: lanza ObjectNotFoundException")
    void notFound_throwsObjectNotFoundException() {
        when(raffleRuleRespository.findByRaffleIdAndRuleType(1L, TicketEarningRuleType.PURCHASE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByRaffleIdAndRuleType(1L, TicketEarningRuleType.PURCHASE))
                .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    @DisplayName("raffleId inválido: lanza IllegalArgumentException")
    void invalidRaffleId_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.getByRaffleIdAndRuleType(0L, TicketEarningRuleType.PURCHASE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
