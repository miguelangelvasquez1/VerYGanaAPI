package com.verygana2.services.raffles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.raffle.responses.TicketEarningResult;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleRule;
import com.verygana2.models.raffles.TicketEarningRule;
import com.verygana2.repositories.raffles.RaffleTicketRepository;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.raffles.RaffleService;
import com.verygana2.services.interfaces.raffles.RaffleTicketService;

@ExtendWith(MockitoExtension.class)
@DisplayName("TicketDeliveryServiceImpl")
class TicketDeliveryServiceImplTest {

    @Mock RaffleTicketService raffleTicketService;
    @Mock RaffleTicketRepository raffleTicketRepository;
    @Mock RaffleService raffleService;
    @Mock NotificationService notificationService;

    @InjectMocks TicketDeliveryServiceImpl service;

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Raffle activeRaffleWithPurchaseRule(BigDecimal minAmount, int ticketsToAward) {
        TicketEarningRule earningRule = new TicketEarningRule();
        earningRule.setRuleType(TicketEarningRuleType.PURCHASE);
        earningRule.setMinPurchaseAmount(minAmount);
        earningRule.setTicketsToAward(ticketsToAward);
        earningRule.setActive(true);

        RaffleRule rule = new RaffleRule();
        rule.setId(1L);
        rule.setActive(true);
        rule.setTicketEarningRule(earningRule);

        Raffle raffle = new Raffle();
        raffle.setId(10L);
        raffle.setTitle("Active Raffle");
        raffle.setRaffleStatus(RaffleStatus.ACTIVE);
        raffle.setRaffleType(RaffleType.STANDARD);
        raffle.setDrawDate(ZonedDateTime.now().plusDays(7));
        raffle.setRaffleRules(List.of(rule));
        return raffle;
    }

    // ─── validateInputs ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("processTicketEarningForPurchase — input validation")
    class InputValidation {

        @Test
        @DisplayName("throws InvalidRequestException for null consumer ID")
        void throwsOnNullConsumerId() {
            assertThatThrownBy(() -> service.processTicketEarningForPurchase(null, 1L, BigDecimal.TEN))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Consumer ID");
        }

        @Test
        @DisplayName("throws InvalidRequestException for non-positive consumer ID")
        void throwsOnNonPositiveConsumerId() {
            assertThatThrownBy(() -> service.processTicketEarningForPurchase(0L, 1L, BigDecimal.TEN))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("throws InvalidRequestException for null purchase ID")
        void throwsOnNullPurchaseId() {
            assertThatThrownBy(() -> service.processTicketEarningForPurchase(1L, null, BigDecimal.TEN))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Purchase ID");
        }

        @Test
        @DisplayName("throws InvalidRequestException for non-positive purchase ID")
        void throwsOnNonPositivePurchaseId() {
            assertThatThrownBy(() -> service.processTicketEarningForPurchase(1L, 0L, BigDecimal.TEN))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("throws InvalidRequestException for null purchase amount")
        void throwsOnNullAmount() {
            assertThatThrownBy(() -> service.processTicketEarningForPurchase(1L, 1L, null))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("amount");
        }

        @Test
        @DisplayName("throws InvalidRequestException for zero purchase amount")
        void throwsOnZeroAmount() {
            assertThatThrownBy(() -> service.processTicketEarningForPurchase(1L, 1L, BigDecimal.ZERO))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("amount");
        }
    }

    // ─── no active raffles ────────────────────────────────────────────────────

    @Nested
    @DisplayName("processTicketEarningForPurchase — no active raffles")
    class NoActiveRaffles {

        @Test
        @DisplayName("returns empty result when no active raffles exist")
        void returnsEmptyWhenNoRaffles() {
            when(raffleService.getActiveRafflesOrderedByDrawDate(any())).thenReturn(List.of());

            TicketEarningResult result = service.processTicketEarningForPurchase(1L, 5L, new BigDecimal("10000"));

            assertThat(result.getTotalTicketsIssued()).isEqualTo(0);
            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ─── idempotency ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("processTicketEarningForPurchase — idempotency")
    class Idempotency {

        @Test
        @DisplayName("returns empty result when tickets already issued for this purchase")
        void returnsEmptyWhenAlreadyIssued() {
            Raffle raffle = activeRaffleWithPurchaseRule(new BigDecimal("5000"), 2);
            when(raffleService.getActiveRafflesOrderedByDrawDate(any())).thenReturn(List.of(raffle));
            when(raffleTicketRepository.existsByTicketOwnerIdAndSourceAndSourceId(
                    1L, RaffleTicketSource.PURCHASE, 5L)).thenReturn(true);

            TicketEarningResult result = service.processTicketEarningForPurchase(1L, 5L, new BigDecimal("10000"));

            assertThat(result.getTotalTicketsIssued()).isEqualTo(0);
            verify(raffleTicketService, never()).issueTickets(any(), any(), any(), any(), any());
        }
    }

    // ─── purchase amount below minimum ────────────────────────────────────────

    @Nested
    @DisplayName("processTicketEarningForPurchase — amount below minimum")
    class AmountBelowMinimum {

        @Test
        @DisplayName("returns empty result when purchase amount does not meet min threshold")
        void returnsEmptyWhenAmountTooLow() {
            Raffle raffle = activeRaffleWithPurchaseRule(new BigDecimal("50000"), 3);
            when(raffleService.getActiveRafflesOrderedByDrawDate(any())).thenReturn(List.of(raffle));
            when(raffleTicketRepository.existsByTicketOwnerIdAndSourceAndSourceId(any(), any(), any()))
                    .thenReturn(false);

            TicketEarningResult result = service.processTicketEarningForPurchase(1L, 5L, new BigDecimal("1000"));

            assertThat(result.getTotalTicketsIssued()).isEqualTo(0);
            verify(raffleTicketService, never()).issueTickets(any(), any(), any(), any(), any());
        }
    }

    // ─── raffle without purchase rule ─────────────────────────────────────────

    @Nested
    @DisplayName("processTicketEarningForPurchase — raffle without PURCHASE rule")
    class NoActivePurchaseRule {

        @Test
        @DisplayName("skips raffle that has no active PURCHASE rule")
        void skipsRaffleWithoutPurchaseRule() {
            Raffle raffle = new Raffle();
            raffle.setId(10L);
            raffle.setTitle("No Rule Raffle");
            raffle.setRaffleRules(List.of());

            when(raffleService.getActiveRafflesOrderedByDrawDate(any())).thenReturn(List.of(raffle));
            when(raffleTicketRepository.existsByTicketOwnerIdAndSourceAndSourceId(any(), any(), any()))
                    .thenReturn(false);

            TicketEarningResult result = service.processTicketEarningForPurchase(1L, 5L, new BigDecimal("10000"));

            assertThat(result.getTotalTicketsIssued()).isEqualTo(0);
        }
    }

    // ─── successful ticket award ──────────────────────────────────────────────

    @Nested
    @DisplayName("processTicketEarningForPurchase — successful award")
    class SuccessfulAward {

        @Test
        @DisplayName("issues tickets and returns result with totals when conditions met")
        void awardsTicketsSuccessfully() {
            Raffle raffle = activeRaffleWithPurchaseRule(new BigDecimal("5000"), 2);

            com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO dto =
                    new com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO();

            when(raffleService.getActiveRafflesOrderedByDrawDate(any())).thenReturn(List.of(raffle));
            when(raffleTicketRepository.existsByTicketOwnerIdAndSourceAndSourceId(
                    1L, RaffleTicketSource.PURCHASE, 5L)).thenReturn(false);
            when(raffleTicketService.issueTickets(eq(1L), eq(10L), eq(2), eq(RaffleTicketSource.PURCHASE), eq(5L)))
                    .thenReturn(List.of(dto, dto));

            TicketEarningResult result = service.processTicketEarningForPurchase(1L, 5L, new BigDecimal("10000"));

            assertThat(result.getTotalTicketsIssued()).isEqualTo(2);
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("stops at first successful raffle (does not process subsequent raffles)")
        void stopsAfterFirstSuccess() {
            Raffle raffle1 = activeRaffleWithPurchaseRule(new BigDecimal("5000"), 2);
            raffle1.setId(10L);

            Raffle raffle2 = activeRaffleWithPurchaseRule(new BigDecimal("5000"), 3);
            raffle2.setId(20L);

            com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO dto =
                    new com.verygana2.dtos.raffle.responses.RaffleTicketResponseDTO();

            when(raffleService.getActiveRafflesOrderedByDrawDate(any())).thenReturn(List.of(raffle1, raffle2));
            when(raffleTicketRepository.existsByTicketOwnerIdAndSourceAndSourceId(any(), any(), any()))
                    .thenReturn(false);
            when(raffleTicketService.issueTickets(eq(1L), eq(10L), eq(2), any(), any()))
                    .thenReturn(List.of(dto, dto));

            TicketEarningResult result = service.processTicketEarningForPurchase(1L, 5L, new BigDecimal("10000"));

            assertThat(result.getTotalTicketsIssued()).isEqualTo(2);
            verify(raffleTicketService, never()).issueTickets(eq(1L), eq(20L), any(), any(), any());
        }
    }

    // ─── unimplemented methods ────────────────────────────────────────────────

    @Nested
    @DisplayName("unimplemented methods")
    class UnimplementedMethods {

        @Test
        @DisplayName("processTicketEarningForAds throws UnsupportedOperationException")
        void adsThrowsUnsupported() {
            assertThatThrownBy(() -> service.processTicketEarningForAds(1L, 1L, 3))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("processTicketEarningForGames throws UnsupportedOperationException")
        void gamesThrowsUnsupported() {
            assertThatThrownBy(() -> service.processTicketEarningForGames(1L, 1L, 100))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("processTicketEarningForReferral throws UnsupportedOperationException")
        void referralThrowsUnsupported() {
            assertThatThrownBy(() -> service.processTicketEarningForReferral(1L, 1L))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
