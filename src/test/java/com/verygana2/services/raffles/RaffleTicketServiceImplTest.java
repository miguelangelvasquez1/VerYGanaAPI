package com.verygana2.services.raffles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.mappers.raffles.RaffleTicketMapper;
import com.verygana2.exceptions.rafflesExceptions.LimitReachedException;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleTicketStatus;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleRule;
import com.verygana2.models.raffles.RaffleTicket;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.raffles.RaffleParticipationRepository;
import com.verygana2.repositories.raffles.RaffleRepository;
import com.verygana2.repositories.raffles.RaffleRuleRespository;
import com.verygana2.repositories.raffles.RaffleTicketRepository;
import com.verygana2.repositories.raffles.TicketAuditLogRepository;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.raffles.RaffleRuleService;
import com.verygana2.services.interfaces.raffles.RaffleService;

@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleTicketServiceImpl")
class RaffleTicketServiceImplTest {

    @Mock RaffleTicketRepository raffleTicketRepository;
    @Mock RaffleRepository raffleRepository;
    @Mock RaffleRuleRespository raffleRuleRespository;
    @Mock RaffleRuleService raffleRuleService;
    @Mock RaffleService raffleService;
    @Mock RaffleTicketMapper raffleTicketMapper;
    @Mock ConsumerDetailsService consumerDetailsService;
    @Mock RaffleParticipationRepository participationRepository;
    @Mock TicketAuditLogRepository auditLogRepository;

    @InjectMocks RaffleTicketServiceImpl service;

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Raffle activeRaffle(RaffleType type) {
        Raffle r = new Raffle();
        r.setId(10L);
        r.setTitle("Raffle");
        r.setRaffleStatus(RaffleStatus.ACTIVE);
        r.setRaffleType(type);
        r.setEndDate(ZonedDateTime.now().plusDays(1));
        r.setTotalTicketsIssued(0L);
        r.setTotalParticipants(0L);
        r.setMaxTotalTickets(null);
        r.setMaxTicketsPerUser(null);
        return r;
    }

    private ConsumerDetails consumer(Long id, boolean hasPet) {
        ConsumerDetails c = new ConsumerDetails();
        c.setId(id);
        c.setHasPet(hasPet);
        return c;
    }

    private RaffleRule unlimitedRule() {
        RaffleRule rule = new RaffleRule();
        rule.setId(1L);
        rule.setActive(true);
        rule.setMaxTicketsBySource(null);
        rule.setCurrentTicketsBySource(0L);
        return rule;
    }

    // ─── issueTickets — sourceId validation ───────────────────────────────────

    @Nested
    @DisplayName("issueTickets — sourceId validation")
    class SourceIdValidation {

        @Test
        @DisplayName("throws InvalidRequestException when sourceId is null")
        void throwsOnNullSourceId() {
            assertThatThrownBy(() -> service.issueTickets(1L, 10L, 1, RaffleTicketSource.PURCHASE, null))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Source ID");
        }

        @Test
        @DisplayName("throws InvalidRequestException when sourceId is non-positive")
        void throwsOnNonPositiveSourceId() {
            assertThatThrownBy(() -> service.issueTickets(1L, 10L, 1, RaffleTicketSource.PURCHASE, 0L))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Source ID");
        }
    }

    // ─── issueTickets — raffle validation ────────────────────────────────────

    @Nested
    @DisplayName("issueTickets — raffle validation")
    class RaffleValidation {

        @Test
        @DisplayName("throws InvalidRequestException when raffle is not ACTIVE")
        void throwsWhenRaffleNotActive() {
            Raffle raffle = activeRaffle(RaffleType.STANDARD);
            raffle.setRaffleStatus(RaffleStatus.CLOSED);
            when(raffleService.getRaffleById(10L)).thenReturn(raffle);

            assertThatThrownBy(() -> service.issueTickets(1L, 10L, 1, RaffleTicketSource.PURCHASE, 5L))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("status");
        }

        @Test
        @DisplayName("throws InvalidRequestException when raffle has already ended")
        void throwsWhenRaffleExpired() {
            Raffle raffle = activeRaffle(RaffleType.STANDARD);
            raffle.setEndDate(ZonedDateTime.now().minusDays(1));
            when(raffleService.getRaffleById(10L)).thenReturn(raffle);

            assertThatThrownBy(() -> service.issueTickets(1L, 10L, 1, RaffleTicketSource.PURCHASE, 5L))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("ended");
        }
    }

    // ─── issueTickets — eligibility ───────────────────────────────────────────

    @Nested
    @DisplayName("issueTickets — eligibility")
    class EligibilityValidation {

        @Test
        @DisplayName("throws InvalidRequestException when PREMIUM raffle and consumer has no pet")
        void throwsWhenPremiumAndNoPet() {
            Raffle raffle = activeRaffle(RaffleType.PREMIUM);
            ConsumerDetails consumer = consumer(1L, false);

            when(raffleService.getRaffleById(10L)).thenReturn(raffle);
            when(consumerDetailsService.getConsumerById(1L)).thenReturn(consumer);

            assertThatThrownBy(() -> service.issueTickets(1L, 10L, 1, RaffleTicketSource.PURCHASE, 5L))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("pet");
        }
    }

    // ─── issueTickets — limits ────────────────────────────────────────────────

    @Nested
    @DisplayName("issueTickets — limits")
    class LimitValidation {

        @Test
        @DisplayName("throws LimitReachedException when raffle total limit is reached")
        void throwsWhenTotalLimitReached() {
            Raffle raffle = activeRaffle(RaffleType.STANDARD);
            raffle.setMaxTotalTickets(5L);
            raffle.setTotalTicketsIssued(5L);

            ConsumerDetails consumer = consumer(1L, false);
            RaffleRule rule = unlimitedRule();

            when(raffleService.getRaffleById(10L)).thenReturn(raffle);
            when(consumerDetailsService.getConsumerById(1L)).thenReturn(consumer);
            when(raffleRuleService.getByRaffleIdAndRuleType(any(), any())).thenReturn(rule);

            assertThatThrownBy(() -> service.issueTickets(1L, 10L, 1, RaffleTicketSource.PURCHASE, 5L))
                    .isInstanceOf(LimitReachedException.class)
                    .hasMessageContaining("maximum total");
        }

        @Test
        @DisplayName("throws LimitReachedException when per-user limit would be exceeded")
        void throwsWhenUserLimitExceeded() {
            Raffle raffle = activeRaffle(RaffleType.STANDARD);
            raffle.setMaxTicketsPerUser(3L);
            raffle.setTotalTicketsIssued(0L);

            ConsumerDetails consumer = consumer(1L, false);
            RaffleRule rule = unlimitedRule();

            when(raffleService.getRaffleById(10L)).thenReturn(raffle);
            when(consumerDetailsService.getConsumerById(1L)).thenReturn(consumer);
            when(raffleRuleService.getByRaffleIdAndRuleType(any(), any())).thenReturn(rule);
            when(raffleTicketRepository.countByTicketOwnerIdAndRaffleIdAndStatus(1L, 10L, RaffleTicketStatus.ACTIVE))
                    .thenReturn(3L);

            assertThatThrownBy(() -> service.issueTickets(1L, 10L, 1, RaffleTicketSource.PURCHASE, 5L))
                    .isInstanceOf(LimitReachedException.class)
                    .hasMessageContaining("maximum tickets per user");
        }
    }

    // ─── issueTickets — success ───────────────────────────────────────────────

    @Nested
    @DisplayName("issueTickets — success")
    class IssueTicketsSuccess {

        @Test
        @DisplayName("issues tickets and returns mapped DTOs")
        void issuesTicketsSuccessfully() {
            Raffle raffle = activeRaffle(RaffleType.STANDARD);
            ConsumerDetails consumer = consumer(1L, false);
            RaffleRule rule = unlimitedRule();

            RaffleTicket savedTicket = new RaffleTicket();
            com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO dto =
                    new com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO();

            when(raffleService.getRaffleById(10L)).thenReturn(raffle);
            when(consumerDetailsService.getConsumerById(1L)).thenReturn(consumer);
            when(raffleRuleService.getByRaffleIdAndRuleType(any(), any())).thenReturn(rule);
            when(raffleTicketRepository.saveAll(any())).thenReturn(List.of(savedTicket));
            when(raffleRepository.save(raffle)).thenReturn(raffle);
            when(raffleRuleRespository.save(rule)).thenReturn(rule);
            when(participationRepository.findByConsumerIdAndRaffleId(1L, 10L))
                    .thenReturn(java.util.Optional.empty());
            when(participationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(auditLogRepository.saveAll(any())).thenReturn(List.of());
            when(raffleTicketMapper.toRaffleTicketResponseDTO(savedTicket)).thenReturn(dto);

            List<com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO> result =
                    service.issueTickets(1L, 10L, 1, RaffleTicketSource.PURCHASE, 5L);

            assertThat(result).containsExactly(dto);
        }
    }

    // ─── canUserReceiveTickets ────────────────────────────────────────────────

    @Nested
    @DisplayName("canUserReceiveTickets")
    class CanUserReceiveTickets {

        @Test
        @DisplayName("throws IllegalArgumentException for null consumer ID")
        void throwsOnNullId() {
            assertThatThrownBy(() -> service.canUserReceiveTickets(null, RaffleType.STANDARD))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("returns true for STANDARD raffle regardless of pet status")
        void returnsTrueForStandard() {
            assertThat(service.canUserReceiveTickets(1L, RaffleType.STANDARD)).isTrue();
        }

        @Test
        @DisplayName("returns true for PREMIUM raffle when consumer has a pet")
        void returnsTrueForPremiumWithPet() {
            when(consumerDetailsService.getConsumerById(1L)).thenReturn(consumer(1L, true));

            assertThat(service.canUserReceiveTickets(1L, RaffleType.PREMIUM)).isTrue();
        }

        @Test
        @DisplayName("returns false for PREMIUM raffle when consumer has no pet")
        void returnsFalseForPremiumWithoutPet() {
            when(consumerDetailsService.getConsumerById(1L)).thenReturn(consumer(1L, false));

            assertThat(service.canUserReceiveTickets(1L, RaffleType.PREMIUM)).isFalse();
        }
    }

    // ─── getUserTicketBalanceInRaffle ─────────────────────────────────────────

    @Nested
    @DisplayName("getUserTicketBalanceInRaffle")
    class GetUserTicketBalanceInRaffle {

        @Test
        @DisplayName("throws IllegalArgumentException for null consumer ID")
        void throwsOnNullConsumerId() {
            assertThatThrownBy(() -> service.getUserTicketBalanceInRaffle(null, 1L, RaffleTicketStatus.ACTIVE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null raffle ID")
        void throwsOnNullRaffleId() {
            assertThatThrownBy(() -> service.getUserTicketBalanceInRaffle(1L, null, RaffleTicketStatus.ACTIVE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("returns count from repository")
        void returnsCountFromRepo() {
            when(raffleTicketRepository.countByTicketOwnerIdAndRaffleIdAndStatus(1L, 10L, RaffleTicketStatus.ACTIVE))
                    .thenReturn(5L);

            Long count = service.getUserTicketBalanceInRaffle(1L, 10L, RaffleTicketStatus.ACTIVE);

            assertThat(count).isEqualTo(5L);
        }
    }

    // ─── getUserTotalTickets ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getUserTotalTickets")
    class GetUserTotalTickets {

        @Test
        @DisplayName("throws IllegalArgumentException for null consumer ID")
        void throwsOnNullId() {
            assertThatThrownBy(() -> service.getUserTotalTickets(null, RaffleTicketStatus.ACTIVE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("returns count from repository")
        void returnsCountFromRepo() {
            when(raffleTicketRepository.countByTicketOwnerIdAndStatus(1L, RaffleTicketStatus.ACTIVE))
                    .thenReturn(12L);

            Long count = service.getUserTotalTickets(1L, RaffleTicketStatus.ACTIVE);

            assertThat(count).isEqualTo(12L);
        }
    }
}
