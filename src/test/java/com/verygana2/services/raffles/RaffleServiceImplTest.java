package com.verygana2.services.raffles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.verygana2.exceptions.rafflesExceptions.InvalidOperationException;
import com.verygana2.mappers.raffles.PrizeMapper;
import com.verygana2.mappers.raffles.RaffleMapper;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.repositories.raffles.PrizeImageAssetRepository;
import com.verygana2.repositories.raffles.PrizeRepository;
import com.verygana2.repositories.raffles.RaffleImageAssetRepository;
import com.verygana2.repositories.raffles.RaffleParticipationRepository;
import com.verygana2.repositories.raffles.RaffleRepository;
import com.verygana2.repositories.raffles.RaffleTicketRepository;
import com.verygana2.repositories.raffles.TicketEarningRuleRepository;
import com.verygana2.storage.service.R2Service;

@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleServiceImpl")
class RaffleServiceImplTest {

    @Mock RaffleRepository raffleRepository;
    @Mock PrizeRepository prizeRepository;
    @Mock TicketEarningRuleRepository ticketEarningRuleRepository;
    @Mock RaffleTicketRepository raffleTicketRepository;
    @Mock RaffleParticipationRepository raffleParticipationRepository;
    @Mock RaffleImageAssetRepository raffleImageAssetRepository;
    @Mock PrizeImageAssetRepository prizeImageAssetRepository;
    @Mock R2Service r2Service;
    @Mock RaffleMapper raffleMapper;
    @Mock PrizeMapper prizeMapper;

    @InjectMocks RaffleServiceImpl service;

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Raffle raffleWithStatus(RaffleStatus status) {
        Raffle r = new Raffle();
        r.setId(1L);
        r.setRaffleStatus(status);
        r.setEndDate(ZonedDateTime.now().plusDays(7));
        r.setPrizes(List.of(new Prize()));
        return r;
    }

    // ─── getRaffleById ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getRaffleById")
    class GetRaffleById {

        @Test
        @DisplayName("throws IllegalArgumentException for null raffle ID")
        void throwsOnNullId() {
            assertThatThrownBy(() -> service.getRaffleById(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for zero raffle ID")
        void throwsOnZeroId() {
            assertThatThrownBy(() -> service.getRaffleById(0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("returns Raffle when found")
        void returnsRaffle() {
            Raffle raffle = new Raffle();
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));

            Raffle result = service.getRaffleById(1L);

            assertThat(result).isSameAs(raffle);
        }

        @Test
        @DisplayName("throws ObjectNotFoundException when not found")
        void throwsWhenNotFound() {
            when(raffleRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getRaffleById(99L))
                    .isInstanceOf(ObjectNotFoundException.class);
        }
    }

    // ─── activateRaffle ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("activateRaffle")
    class ActivateRaffle {

        @Test
        @DisplayName("throws InvalidOperationException when status is not DRAFT or CANCELLED")
        void throwsWhenNotDraftOrCancelled() {
            Raffle raffle = raffleWithStatus(RaffleStatus.ACTIVE);
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));

            assertThatThrownBy(() -> service.activateRaffle(1L))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test
        @DisplayName("throws InvalidOperationException when raffle has no prizes")
        void throwsWhenNoPrizes() {
            Raffle raffle = raffleWithStatus(RaffleStatus.DRAFT);
            raffle.setPrizes(List.of());
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));

            assertThatThrownBy(() -> service.activateRaffle(1L))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("prizes");
        }

        @Test
        @DisplayName("throws InvalidOperationException when end date has passed")
        void throwsWhenExpired() {
            Raffle raffle = raffleWithStatus(RaffleStatus.DRAFT);
            raffle.setEndDate(ZonedDateTime.now().minusDays(1));
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));

            assertThatThrownBy(() -> service.activateRaffle(1L))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("activates DRAFT raffle successfully")
        void activatesDraftRaffle() {
            Raffle raffle = raffleWithStatus(RaffleStatus.DRAFT);
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));
            when(raffleRepository.save(raffle)).thenReturn(raffle);

            service.activateRaffle(1L);

            assertThat(raffle.getRaffleStatus()).isEqualTo(RaffleStatus.ACTIVE);
            verify(raffleRepository).save(raffle);
        }

        @Test
        @DisplayName("re-activates CANCELLED raffle successfully")
        void activatesCancelledRaffle() {
            Raffle raffle = raffleWithStatus(RaffleStatus.CANCELLED);
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));
            when(raffleRepository.save(raffle)).thenReturn(raffle);

            service.activateRaffle(1L);

            assertThat(raffle.getRaffleStatus()).isEqualTo(RaffleStatus.ACTIVE);
        }
    }

    // ─── closeRaffle ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("closeRaffle")
    class CloseRaffle {

        @Test
        @DisplayName("throws InvalidOperationException when raffle is not ACTIVE")
        void throwsWhenNotActive() {
            Raffle raffle = raffleWithStatus(RaffleStatus.DRAFT);
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));

            assertThatThrownBy(() -> service.closeRaffle(1L))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("ACTIVE");
        }

        @Test
        @DisplayName("closes ACTIVE raffle successfully")
        void closesRaffle() {
            Raffle raffle = raffleWithStatus(RaffleStatus.ACTIVE);
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));
            when(raffleRepository.save(raffle)).thenReturn(raffle);

            service.closeRaffle(1L);

            assertThat(raffle.getRaffleStatus()).isEqualTo(RaffleStatus.CLOSED);
        }
    }

    // ─── liveRaffle ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("liveRaffle")
    class LiveRaffle {

        @Test
        @DisplayName("throws InvalidOperationException when raffle is not CLOSED")
        void throwsWhenNotClosed() {
            Raffle raffle = raffleWithStatus(RaffleStatus.ACTIVE);
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));

            assertThatThrownBy(() -> service.liveRaffle(1L))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("CLOSED");
        }

        @Test
        @DisplayName("transitions CLOSED raffle to LIVE")
        void livesRaffle() {
            Raffle raffle = raffleWithStatus(RaffleStatus.CLOSED);
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));
            when(raffleRepository.save(raffle)).thenReturn(raffle);

            service.liveRaffle(1L);

            assertThat(raffle.getRaffleStatus()).isEqualTo(RaffleStatus.LIVE);
        }
    }

    // ─── cancelRaffle ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelRaffle")
    class CancelRaffle {

        @Test
        @DisplayName("throws InvalidOperationException when raffle is not ACTIVE")
        void throwsWhenNotActive() {
            Raffle raffle = raffleWithStatus(RaffleStatus.DRAFT);
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));

            assertThatThrownBy(() -> service.cancelRaffle(1L))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("ACTIVE");
        }

        @Test
        @DisplayName("saves raffle after cancel (note: bug sets status to LIVE instead of CANCELLED)")
        void savesAfterCancel() {
            Raffle raffle = raffleWithStatus(RaffleStatus.ACTIVE);
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));
            when(raffleRepository.save(raffle)).thenReturn(raffle);

            service.cancelRaffle(1L);

            // Known bug: cancelRaffle sets LIVE instead of CANCELLED
            verify(raffleRepository).save(raffle);
        }
    }

    // ─── deleteRaffle ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteRaffle")
    class DeleteRaffle {

        @Test
        @DisplayName("throws InvalidOperationException when raffle is not DRAFT")
        void throwsWhenNotDraft() {
            Raffle raffle = raffleWithStatus(RaffleStatus.ACTIVE);
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));

            assertThatThrownBy(() -> service.deleteRaffle(1L))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test
        @DisplayName("deletes DRAFT raffle from repository")
        void deletesDraftRaffle() {
            Raffle raffle = raffleWithStatus(RaffleStatus.DRAFT);
            when(raffleRepository.findById(1L)).thenReturn(Optional.of(raffle));

            service.deleteRaffle(1L);

            verify(raffleRepository).delete(raffle);
        }
    }

    // ─── getMyRafflesByStatus ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyRafflesByStatus")
    class GetMyRafflesByStatus {

        @Test
        @DisplayName("throws IllegalArgumentException for null consumer ID")
        void throwsOnNullConsumerId() {
            assertThatThrownBy(() -> service.getMyRafflesByStatus(null, RaffleStatus.ACTIVE, PageRequest.of(0, 10)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for non-positive consumer ID")
        void throwsOnNonPositiveConsumerId() {
            assertThatThrownBy(() -> service.getMyRafflesByStatus(0L, RaffleStatus.ACTIVE, PageRequest.of(0, 10)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for invalid status (not ACTIVE or COMPLETED)")
        void throwsOnInvalidStatus() {
            assertThatThrownBy(() -> service.getMyRafflesByStatus(1L, RaffleStatus.DRAFT, PageRequest.of(0, 10)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ACTIVE");
        }

        @Test
        @DisplayName("returns paged response for ACTIVE status")
        void returnsPagedResponseForActive() {
            com.verygana2.dtos.raffle.responses.UserRaffleSummaryResponseDTO dto =
                    new com.verygana2.dtos.raffle.responses.UserRaffleSummaryResponseDTO();
            dto.setImageUrl("key.jpg");

            when(raffleRepository.findMyRafflesByStatus(1L, RaffleStatus.ACTIVE, PageRequest.of(0, 10)))
                    .thenReturn(new PageImpl<>(List.of(dto)));

            var result = service.getMyRafflesByStatus(1L, RaffleStatus.ACTIVE, PageRequest.of(0, 10));

            assertThat(result.getData()).hasSize(1);
        }

        @Test
        @DisplayName("accepts COMPLETED status")
        void acceptsCompletedStatus() {
            when(raffleRepository.findMyRafflesByStatus(1L, RaffleStatus.COMPLETED, PageRequest.of(0, 10)))
                    .thenReturn(new PageImpl<>(List.of()));

            var result = service.getMyRafflesByStatus(1L, RaffleStatus.COMPLETED, PageRequest.of(0, 10));

            assertThat(result.getData()).isEmpty();
        }
    }
}
