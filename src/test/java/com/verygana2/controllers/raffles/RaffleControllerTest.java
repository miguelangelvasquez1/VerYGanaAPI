package com.verygana2.controllers.raffles;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.raffle.responses.RaffleResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleSummaryResponseDTO;
import com.verygana2.dtos.raffle.responses.UserRaffleSummaryResponseDTO;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.services.interfaces.raffles.RaffleService;
import com.verygana2.services.interfaces.raffles.WaitingRoomService;
import com.verygana2.services.raffles.RaffleDrawStateCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link RaffleController}: delegación de cada endpoint al service
 * correspondiente, incluyendo el estado del sorteo en vivo que combina
 * {@code RaffleService}, {@code WaitingRoomService} y el cache en memoria
 * {@code RaffleDrawStateCache} (instancia real, es solo un mapa concurrente).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleController")
class RaffleControllerTest {

    @Mock private RaffleService raffleService;
    @Mock private WaitingRoomService waitingRoomService;

    private RaffleController controller;

    @BeforeEach
    void setUp() {
        controller = new RaffleController(raffleService, new RaffleDrawStateCache(), waitingRoomService);
    }

    private Jwt jwtWithUserId(Long userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(userId);
        return jwt;
    }

    @Test
    @DisplayName("getSummaryRafflesByFilters: delega con status/search/drawDate/type/pageable")
    void getSummaryRafflesByFilters_delegates() {
        var pageable = PageRequest.of(0, 10);
        var drawDate = LocalDate.of(2026, 7, 13);
        var expected = PagedResponse.<RaffleSummaryResponseDTO>builder().build();
        when(raffleService.getSummaryRafflesByFilters(RaffleStatus.ACTIVE, "sorteo", drawDate, RaffleType.STANDARD,
                pageable)).thenReturn(expected);

        var response = controller.getSummaryRafflesByFilters(RaffleStatus.ACTIVE, "sorteo", drawDate,
                RaffleType.STANDARD, pageable);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getRaffleById: delega en el service con el raffleId del path")
    void getRaffleById_delegates() {
        RaffleResponseDTO expected = new RaffleResponseDTO();
        when(raffleService.getRaffleResponseDTOById(1L)).thenReturn(expected);

        assertThat(controller.getRaffleById(1L).getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getDrawStatus: combina el conteo de espectadores de WaitingRoomService con los datos de la rifa")
    void getDrawStatus_combinesViewerCountAndRaffleData() {
        Raffle raffle = new Raffle();
        raffle.setId(1L);
        raffle.setDrawDate(java.time.ZonedDateTime.now().plusMinutes(5));
        raffle.setTotalParticipants(10);
        when(raffleService.getRaffleById(1L)).thenReturn(raffle);
        when(waitingRoomService.getViewerCount(1L)).thenReturn(7);

        var response = controller.getDrawStatus(1L);

        assertThat(response.getBody().getViewerCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("getMyRafflesByStatus: extrae el consumerId del JWT")
    void getMyRafflesByStatus_delegates() {
        var pageable = PageRequest.of(0, 10);
        var expected = PagedResponse.<UserRaffleSummaryResponseDTO>builder().build();
        when(raffleService.getMyRafflesByStatus(9L, RaffleStatus.ACTIVE, pageable)).thenReturn(expected);

        var response = controller.getMyRafflesByStatus(jwtWithUserId(9L), RaffleStatus.ACTIVE, pageable);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("countMyRafflesByStatus: extrae el consumerId del JWT")
    void countMyRafflesByStatus_delegates() {
        when(raffleService.countMyRafflesByStatus(9L, RaffleStatus.COMPLETED)).thenReturn(3L);

        var response = controller.countMyRafflesByStatus(jwtWithUserId(9L), RaffleStatus.COMPLETED);

        assertThat(response.getBody()).isEqualTo(3L);
    }
}
