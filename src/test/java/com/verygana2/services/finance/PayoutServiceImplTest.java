package com.verygana2.services.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.payout.PayoutResponseDTO;
import com.verygana2.models.enums.finance.CopaymentStatus;
import com.verygana2.models.enums.finance.PayoutStatus;
import com.verygana2.models.finance.Copayment;
import com.verygana2.models.finance.Payout;
import com.verygana2.models.finance.PayoutItem;
import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.CopaymentRepository;
import com.verygana2.repositories.finance.PayoutItemRepository;
import com.verygana2.repositories.finance.PayoutRepository;
import com.verygana2.services.interfaces.finance.TreasuryService;

@ExtendWith(MockitoExtension.class)
@DisplayName("PayoutServiceImpl")
class PayoutServiceImplTest {

    @Mock PayoutRepository payoutRepository;
    @Mock PayoutItemRepository payoutItemRepository;
    @Mock CopaymentRepository copaymentRepository;
    @Mock TreasuryService treasuryService;

    @InjectMocks PayoutServiceImpl service;

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private PayoutMethod verifiedPayoutMethod() {
        return PayoutMethod.builder()
                .id(1L)
                .verified(true)
                .active(true)
                .build();
    }

    private CommercialDetails commercial(Long id, String name, boolean withPayoutMethod) {
        CommercialDetails c = new CommercialDetails();
        c.setId(id);
        c.setCompanyName(name);
        if (withPayoutMethod) {
            c.setDefaultPayoutMethod(verifiedPayoutMethod());
        }
        return c;
    }

    private Payout buildPayout(UUID id, PayoutStatus status, CommercialDetails commercial, long netCents) {
        return Payout.builder()
                .id(id)
                .commercial(commercial)
                .grossAmountCents(netCents)
                .commissionCents(0L)
                .netAmountCents(netCents)
                .commissionPctApplied(0)
                .status(status)
                .scheduledAt(ZonedDateTime.now(ZoneOffset.UTC))
                .retryCount(0)
                .build();
    }

    // ─── scheduleDailyPayouts ─────────────────────────────────────────────────

    @Nested
    @DisplayName("scheduleDailyPayouts")
    class ScheduleDailyPayouts {

        @Test
        @DisplayName("does nothing when no COMPLETED copayments exist")
        void doesNothingWhenNoCopayments() {
            ZonedDateTime start = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1);
            ZonedDateTime end   = ZonedDateTime.now(ZoneOffset.UTC);

            when(copaymentRepository.findCompletedInPeriod(eq(CopaymentStatus.COMPLETED), any(), any()))
                    .thenReturn(List.of());

            service.scheduleDailyPayouts(start, end);

            verify(payoutRepository, never()).save(any());
            verify(payoutItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("all PayoutItems already exist → nothing new scheduled")
        void skipsWhenAllPayoutItemsExist() {
            ZonedDateTime start = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1);
            ZonedDateTime end   = ZonedDateTime.now(ZoneOffset.UTC);

            CommercialDetails commercial = new CommercialDetails();
            commercial.setId(1L);
            commercial.setCompanyName("Empresa A");

            Product product = new Product();
            product.setCommercial(commercial);

            PurchaseItem purchaseItem = new PurchaseItem();
            purchaseItem.setProduct(product);
            purchaseItem.setSubtotalCents(10_000L);
            purchaseItem.setCommissionCents(1_000L);
            purchaseItem.setNetToCommercialCents(9_000L);
            purchaseItem.setCommissionPctApplied(10);

            Purchase purchase = new Purchase();
            purchase.setItems(List.of(purchaseItem));

            Copayment copayment = Copayment.builder()
                    .id(UUID.randomUUID())
                    .status(CopaymentStatus.COMPLETED)
                    .purchase(purchase)
                    .build();

            when(copaymentRepository.findCompletedInPeriod(any(), any(), any()))
                    .thenReturn(List.of(copayment));
            // Simulate idempotency: PayoutItem already exists for this copayment+commercial
            when(payoutItemRepository.existsByCopaymentAndCommercial(copayment.getId(), 1L))
                    .thenReturn(true);

            service.scheduleDailyPayouts(start, end);

            verify(payoutRepository, never()).save(any());
        }

        @Test
        @DisplayName("creates one Payout per commercial with correct amounts")
        void createsPayoutPerCommercial() {
            ZonedDateTime start = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1);
            ZonedDateTime end   = ZonedDateTime.now(ZoneOffset.UTC);

            CommercialDetails commercial = new CommercialDetails();
            commercial.setId(42L);
            commercial.setCompanyName("Empresa B");

            Product product = new Product();
            product.setCommercial(commercial);

            PurchaseItem item = new PurchaseItem();
            item.setProduct(product);
            item.setSubtotalCents(20_000L);
            item.setCommissionCents(2_000L);
            item.setNetToCommercialCents(18_000L);
            item.setCommissionPctApplied(10);

            Purchase purchase = new Purchase();
            purchase.setItems(List.of(item));

            UUID copaymentId = UUID.randomUUID();
            Copayment copayment = Copayment.builder()
                    .id(copaymentId)
                    .status(CopaymentStatus.COMPLETED)
                    .purchase(purchase)
                    .build();

            when(copaymentRepository.findCompletedInPeriod(any(), any(), any()))
                    .thenReturn(List.of(copayment));
            when(payoutItemRepository.existsByCopaymentAndCommercial(copaymentId, 42L))
                    .thenReturn(false);

            Payout savedPayout = buildPayout(UUID.randomUUID(), PayoutStatus.SCHEDULED, commercial, 18_000L);
            when(payoutRepository.save(any())).thenReturn(savedPayout);
            when(payoutItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.scheduleDailyPayouts(start, end);

            ArgumentCaptor<Payout> payoutCaptor = ArgumentCaptor.forClass(Payout.class);
            verify(payoutRepository).save(payoutCaptor.capture());

            Payout captured = payoutCaptor.getValue();
            assertThat(captured.getGrossAmountCents()).isEqualTo(20_000L);
            assertThat(captured.getCommissionCents()).isEqualTo(2_000L);
            assertThat(captured.getNetAmountCents()).isEqualTo(18_000L);
            assertThat(captured.getStatus()).isEqualTo(PayoutStatus.SCHEDULED);

            ArgumentCaptor<PayoutItem> itemCaptor = ArgumentCaptor.forClass(PayoutItem.class);
            verify(payoutItemRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getAmountCents()).isEqualTo(18_000L);
        }
    }

    // ─── processScheduledPayouts ──────────────────────────────────────────────

    @Nested
    @DisplayName("processScheduledPayouts")
    class ProcessScheduledPayouts {

        @Test
        @DisplayName("does nothing when no SCHEDULED payouts exist")
        void doesNothingWhenNoneScheduled() {
            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of());

            service.processScheduledPayouts();

            verify(treasuryService, never()).registerPayoutSent(any(), any());
        }

        @Test
        @DisplayName("marks payout as PAID after successful treasury movement")
        void marksPaidAfterSuccess() {
            CommercialDetails commercial = commercial(1L, "Empresa A", true);

            UUID payoutId = UUID.randomUUID();
            Payout payout = buildPayout(payoutId, PayoutStatus.SCHEDULED, commercial, 10_000L);

            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(payout));
            when(payoutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processScheduledPayouts();

            verify(treasuryService).registerPayoutSent(10_000L, payoutId);
            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PAID);
            assertThat(payout.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("marks payout as FAILED when commercial has no payout method")
        void marksFailedWhenNoPayoutMethod() {
            CommercialDetails commercial = commercial(99L, "Sin método", false); // defaultPayoutMethod = null

            Payout payout = buildPayout(UUID.randomUUID(), PayoutStatus.SCHEDULED, commercial, 5_000L);

            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED)).thenReturn(List.of(payout));
            when(payoutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processScheduledPayouts();

            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
            assertThat(payout.getFailureReason()).isNotBlank();
            verify(treasuryService, never()).registerPayoutSent(any(), any());
        }

        @Test
        @DisplayName("continues processing remaining payouts when one fails")
        void continuesOnError() {
            CommercialDetails goodCommercial = commercial(1L, "OK", true);
            CommercialDetails badCommercial  = commercial(2L, "No método", false);

            UUID goodId = UUID.randomUUID();
            UUID badId  = UUID.randomUUID();
            Payout goodPayout = buildPayout(goodId, PayoutStatus.SCHEDULED, goodCommercial, 10_000L);
            Payout badPayout  = buildPayout(badId,  PayoutStatus.SCHEDULED, badCommercial,  5_000L);

            when(payoutRepository.findByStatus(PayoutStatus.SCHEDULED))
                    .thenReturn(List.of(badPayout, goodPayout));
            when(payoutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processScheduledPayouts();

            assertThat(badPayout.getStatus()).isEqualTo(PayoutStatus.FAILED);
            assertThat(goodPayout.getStatus()).isEqualTo(PayoutStatus.PAID);
        }
    }

    // ─── retryFailedPayouts ───────────────────────────────────────────────────

    @Nested
    @DisplayName("retryFailedPayouts")
    class RetryFailedPayouts {

        @Test
        @DisplayName("does nothing when no FAILED payouts in range")
        void doesNothingWhenNoFailed() {
            ZonedDateTime start = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1);
            ZonedDateTime end   = ZonedDateTime.now(ZoneOffset.UTC);

            when(payoutRepository.findFailedForRetry(any(), any(), any())).thenReturn(List.of());

            service.retryFailedPayouts(start, end);

            verify(treasuryService, never()).registerPayoutSent(any(), any());
        }

        @Test
        @DisplayName("increments retry count and re-processes FAILED payouts")
        void incrementsRetryCountAndReprocesses() {
            CommercialDetails commercial = commercial(1L, "Empresa Retry", true);

            UUID payoutId = UUID.randomUUID();
            Payout failed = buildPayout(payoutId, PayoutStatus.FAILED, commercial, 8_000L);
            failed.setRetryCount(1);
            failed.setFailureReason("timeout");

            ZonedDateTime start = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1);
            ZonedDateTime end   = ZonedDateTime.now(ZoneOffset.UTC);

            when(payoutRepository.findFailedForRetry(eq(PayoutStatus.FAILED), any(), any()))
                    .thenReturn(List.of(failed));
            when(payoutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.retryFailedPayouts(start, end);

            assertThat(failed.getRetryCount()).isEqualTo(2);
            assertThat(failed.getStatus()).isEqualTo(PayoutStatus.PAID);
            assertThat(failed.getFailureReason()).isNull();
            verify(treasuryService).registerPayoutSent(8_000L, payoutId);
        }
    }

    // ─── getPayoutsForDate ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPayoutsForDate")
    class GetPayoutsForDate {

        @Test
        @DisplayName("queries correct UTC date range and returns mapped DTOs")
        void queriesCorrectRange() {
            LocalDate date = LocalDate.of(2025, 3, 15);
            ZonedDateTime expectedStart = date.atStartOfDay(ZoneOffset.UTC);
            ZonedDateTime expectedEnd   = expectedStart.plusDays(1);

            CommercialDetails commercial = new CommercialDetails();
            commercial.setId(1L);
            commercial.setCompanyName("Empresa A");

            Payout payout = buildPayout(UUID.randomUUID(), PayoutStatus.PAID, commercial, 5_000L);

            when(payoutRepository.findByScheduledAtBetweenOrderByScheduledAtDesc(
                    eq(expectedStart), eq(expectedEnd))).thenReturn(List.of(payout));

            List<PayoutResponseDTO> result = service.getPayoutsForDate(date);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).commercialId()).isEqualTo(1L);
            assertThat(result.get(0).netAmountCents()).isEqualTo(5_000L);
            assertThat(result.get(0).status()).isEqualTo(PayoutStatus.PAID);
        }

        @Test
        @DisplayName("returns empty list when no payouts for date")
        void returnsEmptyListWhenNone() {
            when(payoutRepository.findByScheduledAtBetweenOrderByScheduledAtDesc(any(), any()))
                    .thenReturn(List.of());

            List<PayoutResponseDTO> result = service.getPayoutsForDate(LocalDate.now());

            assertThat(result).isEmpty();
        }
    }
}
