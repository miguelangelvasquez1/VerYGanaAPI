package com.verygana2.services.raffles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.exceptions.rafflesExceptions.InvalidOperationException;
import com.verygana2.exceptions.rafflesExceptions.InvalidRaffleStatusException;
import com.verygana2.exceptions.rafflesExceptions.RandomOrgException;
import com.verygana2.models.enums.raffles.DrawMethod;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleTicketStatus;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleResult;
import com.verygana2.models.raffles.RaffleTicket;
import com.verygana2.models.raffles.RaffleWinner;
import com.verygana2.repositories.NotificationRepository;
import com.verygana2.repositories.raffles.PrizeRepository;
import com.verygana2.repositories.raffles.RaffleRepository;
import com.verygana2.repositories.raffles.RaffleResultRepository;
import com.verygana2.repositories.raffles.RaffleTicketRepository;
import com.verygana2.repositories.raffles.RaffleWinnerRepository;
import com.verygana2.services.interfaces.raffles.RaffleEventPublisherService;
import com.verygana2.services.interfaces.raffles.RaffleResultService;
import com.verygana2.services.interfaces.raffles.RaffleService;
import com.verygana2.services.interfaces.raffles.RandomOrgService;

@ExtendWith(MockitoExtension.class)
@DisplayName("DrawingServiceImpl")
class DrawingServiceImplTest {

    @Mock RaffleRepository raffleRepository;
    @Mock RaffleResultRepository raffleResultRepository;
    @Mock RaffleTicketRepository raffleTicketRepository;
    @Mock RaffleService raffleService;
    @Mock RaffleEventPublisherService raffleEventPublisherService;
    @Mock RaffleResultService raffleResultService;
    @Mock RandomOrgService randomOrgService;
    @Mock PrizeRepository prizeRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock RaffleWinnerRepository raffleWinnerRepository;
    @Mock ObjectMapper objectMapper;

    @InjectMocks DrawingServiceImpl service;

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Raffle liveRaffle(DrawMethod method) {
        Raffle r = new Raffle();
        r.setId(1L);
        r.setTitle("Test Raffle");
        r.setRaffleStatus(RaffleStatus.LIVE);
        r.setDrawDate(java.time.ZonedDateTime.now().minusMinutes(1));
        r.setDrawMethod(method);
        r.setTotalTicketsIssued(10L);
        r.setTotalParticipants(10L);
        return r;
    }

    private RaffleTicket ticket(String number) {
        RaffleTicket t = new RaffleTicket();
        t.setId(Long.parseLong(number.replace("T", "")));
        t.setTicketNumber("R1-00000" + number);
        t.setStatus(RaffleTicketStatus.ACTIVE);
        return t;
    }

    // ─── conductDraw — validation ─────────────────────────────────────────────

    @Nested
    @DisplayName("conductDraw — validation")
    class ConductDrawValidation {

        @Test
        @DisplayName("throws IllegalArgumentException for null raffle ID")
        void throwsOnNullId() {
            assertThatThrownBy(() -> service.conductDraw(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for non-positive raffle ID")
        void throwsOnNonPositiveId() {
            assertThatThrownBy(() -> service.conductDraw(0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws InvalidRaffleStatusException when raffle is not LIVE")
        void throwsWhenNotLive() {
            Raffle raffle = new Raffle();
            raffle.setRaffleStatus(RaffleStatus.ACTIVE);
            when(raffleService.getRaffleById(1L)).thenReturn(raffle);

            assertThatThrownBy(() -> service.conductDraw(1L))
                    .isInstanceOf(InvalidRaffleStatusException.class)
                    .hasMessageContaining("LIVE");
        }

        @Test
        @DisplayName("throws InvalidOperationException when draw date has not yet arrived")
        void throwsWhenDrawDateInFuture() {
            Raffle raffle = new Raffle();
            raffle.setRaffleStatus(RaffleStatus.LIVE);
            raffle.setDrawDate(java.time.ZonedDateTime.now().plusHours(1));
            when(raffleService.getRaffleById(1L)).thenReturn(raffle);

            assertThatThrownBy(() -> service.conductDraw(1L))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("draw date");
        }

        @Test
        @DisplayName("throws InvalidOperationException when draw method is null")
        void throwsWhenNoDrawMethod() {
            Raffle raffle = new Raffle();
            raffle.setRaffleStatus(RaffleStatus.LIVE);
            raffle.setDrawDate(java.time.ZonedDateTime.now().minusMinutes(1));
            raffle.setDrawMethod(null);
            when(raffleService.getRaffleById(1L)).thenReturn(raffle);

            assertThatThrownBy(() -> service.conductDraw(1L))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("draw method");
        }

        @Test
        @DisplayName("throws InvalidOperationException when raffle has no prizes")
        void throwsWhenNoPrizes() {
            Raffle raffle = liveRaffle(DrawMethod.SYSTEM_RANDOM);
            when(raffleService.getRaffleById(1L)).thenReturn(raffle);
            when(prizeRepository.findByRaffleIdOrderByPositionAsc(1L)).thenReturn(List.of());

            assertThatThrownBy(() -> service.conductDraw(1L))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("prizes");
        }
    }

    // ─── randomInternalDraw ───────────────────────────────────────────────────

    @Nested
    @DisplayName("randomInternalDraw")
    class RandomInternalDraw {

        @Test
        @DisplayName("throws InvalidOperationException for null ticket list")
        void throwsOnNullTickets() {
            assertThatThrownBy(() -> service.randomInternalDraw(null, 1))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("throws InvalidOperationException for empty ticket list")
        void throwsOnEmptyTickets() {
            assertThatThrownBy(() -> service.randomInternalDraw(List.of(), 1))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("throws InvalidOperationException when numberOfWinners is null")
        void throwsOnNullWinners() {
            assertThatThrownBy(() -> service.randomInternalDraw(List.of(ticket("1")), null))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("throws InvalidOperationException when numberOfWinners is zero")
        void throwsOnZeroWinners() {
            assertThatThrownBy(() -> service.randomInternalDraw(List.of(ticket("1")), 0))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("throws InvalidOperationException when numberOfWinners exceeds ticket count")
        void throwsWhenWinnersExceedTickets() {
            List<RaffleTicket> tickets = List.of(ticket("1"), ticket("2"));

            assertThatThrownBy(() -> service.randomInternalDraw(tickets, 5))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("5 winners from 2");
        }

        @Test
        @DisplayName("returns correct number of winners and marks them as winners")
        void returnsWinnersAndMarksThem() {
            List<RaffleTicket> tickets = new ArrayList<>();
            for (int i = 1; i <= 10; i++) tickets.add(ticket(String.valueOf(i)));

            when(raffleTicketRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            List<RaffleTicket> winners = service.randomInternalDraw(tickets, 3);

            assertThat(winners).hasSize(3);
            winners.forEach(w -> assertThat(w.getIsWinner()).isTrue());
        }
    }

    // ─── randomExternalDraw ───────────────────────────────────────────────────

    @Nested
    @DisplayName("randomExternalDraw")
    class RandomExternalDraw {

        @Test
        @DisplayName("uses Random.org indexes to select winners")
        void selectsWinnersFromRandomOrg() {
            List<RaffleTicket> tickets = new ArrayList<>();
            for (int i = 0; i < 5; i++) tickets.add(ticket(String.valueOf(i + 1)));

            when(randomOrgService.generateRandomIntegers(0, 4, 2)).thenReturn(List.of(0, 3));
            when(raffleTicketRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            List<RaffleTicket> winners = service.randomExternalDraw(tickets, 2);

            assertThat(winners).hasSize(2);
            assertThat(winners.get(0)).isSameAs(tickets.get(0));
            assertThat(winners.get(1)).isSameAs(tickets.get(3));
        }

        @Test
        @DisplayName("falls back to internal draw when RandomOrgException is thrown")
        void fallsBackOnRandomOrgException() {
            List<RaffleTicket> tickets = new ArrayList<>();
            for (int i = 1; i <= 5; i++) tickets.add(ticket(String.valueOf(i)));

            when(randomOrgService.generateRandomIntegers(0, 4, 2))
                    .thenThrow(new RandomOrgException("API down"));
            when(raffleTicketRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            List<RaffleTicket> winners = service.randomExternalDraw(tickets, 2);

            assertThat(winners).hasSize(2);
        }

        @Test
        @DisplayName("throws InvalidOperationException for empty tickets")
        void throwsOnEmptyTickets() {
            assertThatThrownBy(() -> service.randomExternalDraw(List.of(), 1))
                    .isInstanceOf(InvalidOperationException.class);
        }
    }

    // ─── verifyDrawIntegrity ──────────────────────────────────────────────────

    @Nested
    @DisplayName("verifyDrawIntegrity")
    class VerifyDrawIntegrity {

        @Test
        @DisplayName("returns false when drawProof is null")
        void returnsFalseWhenProofNull() {
            Raffle raffle = new Raffle();
            raffle.setRaffleStatus(RaffleStatus.COMPLETED);

            RaffleResult result = new RaffleResult();
            result.setId(1L);
            result.setDrawProof(null);
            result.setRaffle(raffle);

            when(raffleResultService.getByRaffleId(1L)).thenReturn(result);

            assertThat(service.verifyDrawIntegrity(1L)).isFalse();
        }

        @Test
        @DisplayName("returns false when raffle status is not COMPLETED")
        void returnsFalseWhenNotCompleted() {
            Raffle raffle = new Raffle();
            raffle.setRaffleStatus(RaffleStatus.LIVE);

            RaffleResult result = new RaffleResult();
            result.setId(1L);
            result.setDrawProof("{\"valid\":true}");
            result.setRaffle(raffle);

            when(raffleResultService.getByRaffleId(1L)).thenReturn(result);

            assertThat(service.verifyDrawIntegrity(1L)).isFalse();
        }

        @Test
        @DisplayName("returns false when winner count is zero")
        void returnsFalseWhenNoWinners() {
            Raffle raffle = new Raffle();
            raffle.setRaffleStatus(RaffleStatus.COMPLETED);

            RaffleResult result = new RaffleResult();
            result.setId(10L);
            result.setDrawProof("{\"valid\":true}");
            result.setRaffle(raffle);

            when(raffleResultService.getByRaffleId(1L)).thenReturn(result);
            when(raffleWinnerRepository.countByRaffleResultId(10L)).thenReturn(0L);

            assertThat(service.verifyDrawIntegrity(1L)).isFalse();
        }

        @Test
        @DisplayName("returns false when drawProof is invalid JSON")
        void returnsFalseWhenMalformedJson() throws Exception {
            Raffle raffle = new Raffle();
            raffle.setRaffleStatus(RaffleStatus.COMPLETED);

            RaffleResult result = new RaffleResult();
            result.setId(10L);
            result.setDrawProof("{bad-json}");
            result.setRaffle(raffle);

            when(raffleResultService.getByRaffleId(1L)).thenReturn(result);
            when(raffleWinnerRepository.countByRaffleResultId(10L)).thenReturn(1L);
            when(objectMapper.readTree("{bad-json}"))
                    .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "bad"));

            assertThat(service.verifyDrawIntegrity(1L)).isFalse();
        }

        @Test
        @DisplayName("returns true when all integrity checks pass")
        void returnsTrueOnValidProof() throws Exception {
            Raffle raffle = new Raffle();
            raffle.setRaffleStatus(RaffleStatus.COMPLETED);

            RaffleResult result = new RaffleResult();
            result.setId(10L);
            result.setDrawProof("{\"valid\":true}");
            result.setRaffle(raffle);

            when(raffleResultService.getByRaffleId(1L)).thenReturn(result);
            when(raffleWinnerRepository.countByRaffleResultId(10L)).thenReturn(2L);
            when(objectMapper.readTree("{\"valid\":true}"))
                    .thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"valid\":true}"));

            assertThat(service.verifyDrawIntegrity(1L)).isTrue();
        }
    }

    // ─── notifyWinners ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("notifyWinners")
    class NotifyWinners {

        @Test
        @DisplayName("throws IllegalArgumentException for null raffleResultId")
        void throwsOnNullId() {
            assertThatThrownBy(() -> service.notifyWinners(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for non-positive raffleResultId")
        void throwsOnNonPositiveId() {
            assertThatThrownBy(() -> service.notifyWinners(0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("saves notifications for each winner")
        void savesNotificationsForWinners() {
            com.verygana2.models.userDetails.ConsumerDetails consumer =
                    new com.verygana2.models.userDetails.ConsumerDetails();
            consumer.setId(1L);
            consumer.setUserName("Alice");

            RaffleWinner winner = new RaffleWinner();
            winner.setWinner(consumer);

            when(raffleWinnerRepository.findByRaffleResultId(1L)).thenReturn(List.of(winner));
            when(notificationRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            service.notifyWinners(1L);

            verify(notificationRepository).saveAll(any());
        }

        @Test
        @DisplayName("does not call saveAll when there are no winners")
        void skipsWhenNoWinners() {
            when(raffleWinnerRepository.findByRaffleResultId(1L)).thenReturn(List.of());
            when(notificationRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            service.notifyWinners(1L);

            verify(notificationRepository).saveAll(any());
        }
    }
}
