package com.verygana2.controllers.raffles;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO;
import com.verygana2.services.interfaces.raffles.RaffleTicketService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests de {@link UserRaffleTicketController}: mis tickets, balance y elegibilidad, todo desde el JWT del consumidor. */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserRaffleTicketController")
class UserRaffleTicketControllerTest {

    @Mock private RaffleTicketService raffleTicketService;

    private UserRaffleTicketController controller;

    @BeforeEach
    void setUp() {
        controller = new UserRaffleTicketController(raffleTicketService);
    }

    private Jwt jwtWithUserId(Long userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(userId);
        return jwt;
    }

    @Test
    @DisplayName("getUserTicketsByRaffle: delega con consumerId del JWT, raffleId del path y el pageable")
    void getUserTicketsByRaffle_delegates() {
        var pageable = PageRequest.of(0, 10);
        var expected = PagedResponse.<RaffleTicketResponseDTO>builder().build();
        when(raffleTicketService.getUserTicketsByRaffle(9L, 1L, pageable)).thenReturn(expected);

        var response = controller.getUserTicketsByRaffle(jwtWithUserId(9L), 1L, pageable);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getWinnerUserTotalTickets: delega con el consumerId del JWT")
    void getWinnerUserTotalTickets_delegates() {
        when(raffleTicketService.getUserWinnerTotalTickets(9L)).thenReturn(2L);

        assertThat(controller.getWinnerUserTotalTickets(jwtWithUserId(9L)).getBody()).isEqualTo(2L);
    }

}
