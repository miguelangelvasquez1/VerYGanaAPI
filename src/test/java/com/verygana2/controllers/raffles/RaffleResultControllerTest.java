package com.verygana2.controllers.raffles;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.raffle.responses.DrawProofResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleResultResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleSummaryResultResponseDTO;
import com.verygana2.services.interfaces.raffles.RaffleResultService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Tests de {@link RaffleResultController}: consulta pública de resultados y prueba del sorteo. */
@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleResultController")
class RaffleResultControllerTest {

    @Mock private RaffleResultService raffleResultService;

    private RaffleResultController controller;

    @BeforeEach
    void setUp() {
        controller = new RaffleResultController(raffleResultService);
    }

    @Test
    @DisplayName("getRaffleResultByRaffleId: delega en el service con el raffleId del path")
    void getRaffleResultByRaffleId_delegates() {
        RaffleResultResponseDTO expected = new RaffleResultResponseDTO();
        when(raffleResultService.getResultByRaffleId(1L)).thenReturn(expected);

        assertThat(controller.getRaffleResultByRaffleId(1L).getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getLastRaffleResults: delega en el service")
    void getLastRaffleResults_delegates() {
        List<RaffleSummaryResultResponseDTO> expected = List.of(new RaffleSummaryResultResponseDTO());
        when(raffleResultService.getLastRaffleResults()).thenReturn(expected);

        assertThat(controller.getLastRaffleResults().getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getDrawProofByRaffleId: delega en el service con el raffleId del path")
    void getDrawProofByRaffleId_delegates() {
        DrawProofResponseDTO expected = DrawProofResponseDTO.builder().build();
        when(raffleResultService.getDrawProofByRaffleId(1L)).thenReturn(expected);

        assertThat(controller.getDrawProofByRaffleId(1L).getBody()).isSameAs(expected);
    }
}
