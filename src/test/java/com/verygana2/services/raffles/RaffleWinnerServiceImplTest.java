package com.verygana2.services.raffles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.verygana2.dtos.raffle.responses.WinnerSummaryResponseDTO;
import com.verygana2.mappers.raffles.RaffleWinnerMapper;
import com.verygana2.models.raffles.RaffleResult;
import com.verygana2.models.raffles.RaffleWinner;
import com.verygana2.repositories.raffles.RaffleWinnerRepository;
import com.verygana2.services.interfaces.raffles.RaffleResultService;

@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleWinnerServiceImpl")
class RaffleWinnerServiceImplTest {

    @Mock RaffleWinnerRepository raffleWinnerRepository;
    @Mock RaffleResultService raffleResultService;
    @Mock RaffleWinnerMapper raffleWinnerMapper;

    @InjectMocks RaffleWinnerServiceImpl service;

    // ─── getRaffleWinnersByRaffleId ───────────────────────────────────────────

    @Nested
    @DisplayName("getRaffleWinnersByRaffleId")
    class GetRaffleWinnersByRaffleId {

        @Test
        @DisplayName("returns mapped winner summaries for a raffle")
        void returnsMappedWinners() {
            RaffleResult result = new RaffleResult();
            result.setId(10L);

            RaffleWinner winner = new RaffleWinner();
            WinnerSummaryResponseDTO dto = WinnerSummaryResponseDTO.builder().build();

            when(raffleResultService.getByRaffleId(1L)).thenReturn(result);
            when(raffleWinnerRepository.findByRaffleResultId(10L)).thenReturn(List.of(winner));
            when(raffleWinnerMapper.toWinnerSummaryResponseDTO(winner)).thenReturn(dto);

            List<WinnerSummaryResponseDTO> found = service.getRaffleWinnersByRaffleId(1L);

            assertThat(found).containsExactly(dto);
        }

        @Test
        @DisplayName("returns empty list when raffle has no winners")
        void returnsEmptyWhenNoWinners() {
            RaffleResult result = new RaffleResult();
            result.setId(10L);

            when(raffleResultService.getByRaffleId(1L)).thenReturn(result);
            when(raffleWinnerRepository.findByRaffleResultId(10L)).thenReturn(List.of());

            List<WinnerSummaryResponseDTO> found = service.getRaffleWinnersByRaffleId(1L);

            assertThat(found).isEmpty();
        }
    }

    // ─── getLastRaffleWinners ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getLastRaffleWinners")
    class GetLastRaffleWinners {

        @Test
        @DisplayName("returns mapped last winners")
        void returnsMappedLastWinners() {
            RaffleWinner winner = new RaffleWinner();
            WinnerSummaryResponseDTO dto = WinnerSummaryResponseDTO.builder().build();

            when(raffleWinnerRepository.findLastWinners()).thenReturn(List.of(winner));
            when(raffleWinnerMapper.toWinnerSummaryResponseDTO(winner)).thenReturn(dto);

            List<WinnerSummaryResponseDTO> found = service.getLastRaffleWinners();

            assertThat(found).containsExactly(dto);
        }
    }

    // ─── claimPrize ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("claimPrize")
    class ClaimPrize {

        @Test
        @DisplayName("throws UnsupportedOperationException (not yet implemented)")
        void throwsUnsupported() {
            assertThatThrownBy(() -> service.claimPrize())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ─── getWonPrizesList ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getWonPrizesList")
    class GetWonPrizesList {

        @Test
        @DisplayName("returns empty paged response when no prizes won")
        void returnsEmptyPage() {
            when(raffleWinnerRepository.findWonPrizesByConsumer(1L, false, PageRequest.of(0, 10)))
                    .thenReturn(new PageImpl<>(List.of()));

            var result = service.getWonPrizesList(1L, false, PageRequest.of(0, 10));

            assertThat(result.getData()).isEmpty();
        }
    }
}
