package com.verygana2.services.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.models.enums.finance.CopaymentStatus;
import com.verygana2.models.enums.finance.WompiTransactionStatus;
import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.finance.Copayment;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.finance.CopaymentRepository;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.repositories.finance.WompiTransactionRepository;
import com.verygana2.repositories.marketplace.ProductStockRepository;
import com.verygana2.repositories.marketplace.PurchaseRepository;
import com.verygana2.services.interfaces.finance.TreasuryService;

@ExtendWith(MockitoExtension.class)
@DisplayName("CopaymentServiceImpl")
class CopaymentServiceImplTest {

    @Mock CopaymentRepository copaymentRepository;
    @Mock WompiTransactionRepository wompiTransactionRepository;
    @Mock PurchaseRepository purchaseRepository;
    @Mock KeyWalletRepository keyWalletRepository;
    @Mock KeyTransactionRepository keyTransactionRepository;
    @Mock ProductStockRepository productStockRepository;
    @Mock TreasuryService treasuryService;

    @InjectMocks CopaymentServiceImpl service;

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private WompiTransaction wompiTx(UUID id, WompiTransactionStatus status, String reference) {
        return WompiTransaction.builder()
                .id(id)
                .wompiId("wompi-" + id)
                .status(status)
                .type(com.verygana2.models.enums.finance.WompiTransactionType.CHARGE_COPAYMENT)
                .amountInCents(10_000L)
                .reference(reference)
                .build();
    }

    private ConsumerDetails consumer(Long id) {
        ConsumerDetails c = new ConsumerDetails();
        c.setId(id);
        return c;
    }

    private PurchaseItem itemWithStock(String productName, String stockCode) {
        Product product = new Product();
        product.setId(1L);
        product.setName(productName);

        ProductStock stock = ProductStock.builder()
                .id(1L)
                .code(stockCode)
                .status(com.verygana2.models.enums.marketplace.StockStatus.RESERVED)
                .build();

        PurchaseItem item = new PurchaseItem();
        item.setProduct(product);
        item.setAssignedProductStock(stock);
        item.setStatus(PurchaseItemStatus.PENDING);
        return item;
    }

    private Purchase purchaseWithItem(String productName, long commissionCents) {
        PurchaseItem item = itemWithStock(productName, "CODE-ABC");
        Purchase purchase = new Purchase();
        purchase.setItems(List.of(item));
        purchase.setCommissionCents(commissionCents);
        return purchase;
    }

    private Copayment pendingCopayment(UUID id, Purchase purchase, ConsumerDetails consumer,
                                        long keysUsed, long keysValueCents, long cashAmountCents) {
        return Copayment.builder()
                .id(id)
                .purchase(purchase)
                .consumer(consumer)
                .keysUsed(keysUsed)
                .keysValueCents(keysValueCents)
                .cashAmountCents(cashAmountCents)
                .status(CopaymentStatus.PENDING)
                .build();
    }

    // ─── handleWompiResult — null guard ──────────────────────────────────────

    @Nested
    @DisplayName("handleWompiResult — null guard")
    class NullGuard {

        @Test
        @DisplayName("throws NullPointerException for null wompiTransactionId")
        void throwsOnNull() {
            assertThatThrownBy(() -> service.handleWompiResult(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ─── handleWompiResult — idempotency ─────────────────────────────────────

    @Nested
    @DisplayName("handleWompiResult — idempotency")
    class Idempotency {

        @Test
        @DisplayName("does nothing when copayment is already COMPLETED")
        void doesNothingWhenAlreadyCompleted() {
            UUID txId = UUID.randomUUID();
            String ref = "REF-001";

            WompiTransaction tx = wompiTx(txId, WompiTransactionStatus.APPROVED, ref);
            Purchase purchase = purchaseWithItem("Prod", 0L);
            ConsumerDetails c = consumer(1L);

            Copayment copayment = Copayment.builder()
                    .id(UUID.randomUUID())
                    .purchase(purchase)
                    .consumer(c)
                    .keysUsed(0L)
                    .keysValueCents(0L)
                    .cashAmountCents(10_000L)
                    .status(CopaymentStatus.COMPLETED)  // already processed
                    .build();

            when(wompiTransactionRepository.findById(txId)).thenReturn(Optional.of(tx));
            when(copaymentRepository.findByPurchaseReferenceIdWithDetails(ref)).thenReturn(Optional.of(copayment));

            service.handleWompiResult(txId);

            verify(copaymentRepository, never()).save(any());
            verify(treasuryService, never()).moveCashToPayoutPending(any(), any());
        }
    }

    // ─── handleWompiResult — APPROVED flow ───────────────────────────────────

    @Nested
    @DisplayName("handleWompiResult — APPROVED")
    class Approved {

        @Test
        @DisplayName("marks copayment COMPLETED, moves cash to PAYOUTS_PENDING, retains commission")
        void completesAndMovesMoneyNoCashKeys() {
            UUID txId = UUID.randomUUID();
            String ref = "REF-002";

            WompiTransaction tx = wompiTx(txId, WompiTransactionStatus.APPROVED, ref);
            Purchase purchase = purchaseWithItem("Prod", 1_000L);
            ConsumerDetails c = consumer(2L);

            UUID copaymentId = UUID.randomUUID();
            Copayment copayment = pendingCopayment(copaymentId, purchase, c, 0L, 0L, 10_000L);

            when(wompiTransactionRepository.findById(txId)).thenReturn(Optional.of(tx));
            when(copaymentRepository.findByPurchaseReferenceIdWithDetails(ref)).thenReturn(Optional.of(copayment));
            when(copaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(purchaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productStockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.handleWompiResult(txId);

            assertThat(copayment.getStatus()).isEqualTo(CopaymentStatus.COMPLETED);
            verify(treasuryService).moveCashToPayoutPending(10_000L, copaymentId);
            verify(treasuryService).retainCommission(1_000L, copaymentId, "COPAYMENT");
        }

        @Test
        @DisplayName("confirms keys, registers key transaction, converts keys to PAYOUTS_PENDING")
        void convertsKeysWhenKeysUsed() {
            UUID txId = UUID.randomUUID();
            String ref = "REF-003";

            WompiTransaction tx = wompiTx(txId, WompiTransactionStatus.APPROVED, ref);
            Purchase purchase = purchaseWithItem("KeyProd", 0L);
            ConsumerDetails c = consumer(3L);

            UUID copaymentId = UUID.randomUUID();
            // keysUsed=5, keysValueCents=5000, cashAmountCents=5000
            Copayment copayment = pendingCopayment(copaymentId, purchase, c, 5L, 5_000L, 5_000L);

            KeyWallet keyWallet = KeyWallet.builder()
                    .id(UUID.randomUUID())
                    .purchaseKeys(0L)
                    .blockedPurchaseKeys(5L)
                    .build();

            when(wompiTransactionRepository.findById(txId)).thenReturn(Optional.of(tx));
            when(copaymentRepository.findByPurchaseReferenceIdWithDetails(ref)).thenReturn(Optional.of(copayment));
            when(keyWalletRepository.findByConsumerId(3L)).thenReturn(Optional.of(keyWallet));
            when(keyWalletRepository.save(keyWallet)).thenReturn(keyWallet);
            when(keyTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(copaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(purchaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productStockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.handleWompiResult(txId);

            verify(treasuryService).convertKeysToPayoutPending(5_000L, copaymentId);
            verify(treasuryService).moveCashToPayoutPending(5_000L, copaymentId);
            assertThat(copayment.getStatus()).isEqualTo(CopaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("delivers product code to purchase item")
        void deliversProductCode() {
            UUID txId = UUID.randomUUID();
            String ref = "REF-004";

            WompiTransaction tx = wompiTx(txId, WompiTransactionStatus.APPROVED, ref);
            Purchase purchase = purchaseWithItem("Prod", 0L);
            PurchaseItem item = purchase.getItems().get(0);
            ConsumerDetails c = consumer(4L);

            Copayment copayment = pendingCopayment(UUID.randomUUID(), purchase, c, 0L, 0L, 10_000L);

            when(wompiTransactionRepository.findById(txId)).thenReturn(Optional.of(tx));
            when(copaymentRepository.findByPurchaseReferenceIdWithDetails(ref)).thenReturn(Optional.of(copayment));
            when(copaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(purchaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productStockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.handleWompiResult(txId);

            assertThat(item.getDeliveredCode()).isEqualTo("CODE-ABC");
            assertThat(item.getStatus()).isEqualTo(PurchaseItemStatus.DELIVERED);
            assertThat(item.getDeliveredAt()).isNotNull();
        }
    }

    // ─── handleWompiResult — FAILED flow ─────────────────────────────────────

    @Nested
    @DisplayName("handleWompiResult — FAILED/DECLINED")
    class Failed {

        @Test
        @DisplayName("marks copayment FAILED and releases reserved stock")
        void marksFailedAndReleasesStock() {
            UUID txId = UUID.randomUUID();
            String ref = "REF-005";

            WompiTransaction tx = wompiTx(txId, WompiTransactionStatus.DECLINED, ref);
            Purchase purchase = purchaseWithItem("Prod", 0L);
            ProductStock stock = purchase.getItems().get(0).getAssignedProductStock();
            ConsumerDetails c = consumer(5L);

            Copayment copayment = pendingCopayment(UUID.randomUUID(), purchase, c, 0L, 0L, 10_000L);

            when(wompiTransactionRepository.findById(txId)).thenReturn(Optional.of(tx));
            when(copaymentRepository.findByPurchaseReferenceIdWithDetails(ref)).thenReturn(Optional.of(copayment));
            when(copaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(purchaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productStockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.handleWompiResult(txId);

            assertThat(copayment.getStatus()).isEqualTo(CopaymentStatus.FAILED);
            assertThat(copayment.getFailureReason()).isEqualTo("DECLINED");
            assertThat(stock.getStatus()).isEqualTo(com.verygana2.models.enums.marketplace.StockStatus.AVAILABLE);
            verify(treasuryService, never()).moveCashToPayoutPending(any(), any());
        }

        @Test
        @DisplayName("releases reserved keys when keysUsed > 0 on failure")
        void releasesKeysOnFailure() {
            UUID txId = UUID.randomUUID();
            String ref = "REF-006";

            WompiTransaction tx = wompiTx(txId, WompiTransactionStatus.DECLINED, ref);
            Purchase purchase = purchaseWithItem("Prod", 0L);
            ConsumerDetails c = consumer(6L);

            UUID copaymentId = UUID.randomUUID();
            Copayment copayment = pendingCopayment(copaymentId, purchase, c, 3L, 3_000L, 7_000L);

            KeyWallet keyWallet = KeyWallet.builder()
                    .id(UUID.randomUUID())
                    .purchaseKeys(0L)
                    .blockedPurchaseKeys(3L)
                    .build();

            when(wompiTransactionRepository.findById(txId)).thenReturn(Optional.of(tx));
            when(copaymentRepository.findByPurchaseReferenceIdWithDetails(ref)).thenReturn(Optional.of(copayment));
            when(keyWalletRepository.findByConsumerId(6L)).thenReturn(Optional.of(keyWallet));
            when(keyWalletRepository.save(keyWallet)).thenReturn(keyWallet);
            when(keyTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(copaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(purchaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productStockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.handleWompiResult(txId);

            assertThat(copayment.getStatus()).isEqualTo(CopaymentStatus.FAILED);
            assertThat(copayment.getKeysRefundedAt()).isNotNull();
            verify(keyWalletRepository).save(keyWallet);
        }
    }

    // ─── expireStale ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("expireStale")
    class ExpireStale {

        @Test
        @DisplayName("does nothing when no stale copayments exist")
        void doesNothingWhenNoneStale() {
            when(copaymentRepository.findExpiredPending(any(), any())).thenReturn(List.of());

            service.expireStale(30);

            verify(copaymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("expires stale PENDING copayments with PAYMENT_SESSION_EXPIRED reason")
        void expiresStalecopayments() {
            Purchase purchase = purchaseWithItem("Prod", 0L);
            ConsumerDetails c = consumer(7L);

            Copayment stale = pendingCopayment(UUID.randomUUID(), purchase, c, 0L, 0L, 10_000L);

            when(copaymentRepository.findExpiredPending(any(), any())).thenReturn(List.of(stale));
            when(copaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(purchaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productStockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.expireStale(30);

            assertThat(stale.getStatus()).isEqualTo(CopaymentStatus.FAILED);
            assertThat(stale.getFailureReason()).isEqualTo("PAYMENT_SESSION_EXPIRED");
            verify(copaymentRepository).save(stale);
        }

        @Test
        @DisplayName("continues processing remaining copayments when one fails")
        void continuesOnError() {
            Purchase purchase1 = purchaseWithItem("Prod1", 0L);
            ConsumerDetails c1 = consumer(8L);
            Copayment stale1 = pendingCopayment(UUID.randomUUID(), purchase1, c1, 0L, 0L, 5_000L);

            // Copayment with null purchase will throw in handleFailed → should be swallowed
            Copayment stale2 = Copayment.builder()
                    .id(UUID.randomUUID())
                    .purchase(null)
                    .status(CopaymentStatus.PENDING)
                    .build();

            when(copaymentRepository.findExpiredPending(any(), any())).thenReturn(List.of(stale2, stale1));
            when(copaymentRepository.save(stale1)).thenAnswer(inv -> inv.getArgument(0));
            when(purchaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productStockRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Should not throw — errors are caught per copayment
            service.expireStale(30);

            assertThat(stale1.getStatus()).isEqualTo(CopaymentStatus.FAILED);
        }
    }
}
