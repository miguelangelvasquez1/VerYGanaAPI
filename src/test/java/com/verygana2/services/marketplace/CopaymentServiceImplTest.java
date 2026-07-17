package com.verygana2.services.marketplace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.verygana2.models.enums.finance.CopaymentStatus;
import com.verygana2.models.enums.finance.WompiTransactionStatus;
import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.enums.marketplace.PurchaseStatus;
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
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.services.interfaces.raffles.TicketDeliveryService;
import com.verygana2.services.wompi.WompiService;
import com.verygana2.dtos.wompi.WompiTransactionResponseDTO.WompiTransactionData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link CopaymentServiceImpl}: el webhook de Wompi para copagos.
 * Cubre el camino feliz (APPROVED, con y sin llaves), el camino de rechazo
 * (libera stock y llaves), la idempotencia (un mismo webhook reenviado no
 * se procesa dos veces) y la expiración de copagos abandonados.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CopaymentServiceImpl")
class CopaymentServiceImplTest {

    @Mock private CopaymentRepository copaymentRepository;
    @Mock private TicketDeliveryService ticketDeliveryService;
    @Mock private WompiTransactionRepository wompiTransactionRepository;
    @Mock private PurchaseRepository purchaseRepository;
    @Mock private KeyWalletRepository keyWalletRepository;
    @Mock private KeyTransactionRepository keyTransactionRepository;
    @Mock private ProductStockRepository productStockRepository;
    @Mock private TreasuryService treasuryService;
    @Mock private EmailService emailService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private WompiService wompiService;

    private CopaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CopaymentServiceImpl(copaymentRepository, ticketDeliveryService, wompiTransactionRepository,
                purchaseRepository, keyWalletRepository, keyTransactionRepository, productStockRepository,
                treasuryService, emailService, eventPublisher, wompiService);
    }

    private PurchaseItem itemWithStock(Product product) {
        ProductStock stock = ProductStock.builder().status(com.verygana2.models.enums.marketplace.StockStatus.RESERVED).build();
        return PurchaseItem.builder().product(product).assignedProductStock(stock).status(PurchaseItemStatus.PENDING).build();
    }

    private Purchase purchaseWithItems(long totalCents, long commissionCents, PurchaseItem... items) {
        ConsumerDetails consumer = new ConsumerDetails();
        com.verygana2.models.User user = new com.verygana2.models.User();
        user.setId(1L);
        user.setEmail("comprador@test.com");
        consumer.setUser(user);
        // ConsumerDetails.id es un campo propio (mapeado 1-a-1 con user_id vía @MapsId en JPA,
        // pero eso solo aplica al persistir); en memoria hay que setearlo explícitamente, no se
        // deriva de user.getId(). El servicio usa copayment.getConsumer().getId() para el KeyWallet.
        consumer.setId(1L);

        Purchase purchase = Purchase.builder().id(50L).consumer(consumer)
                .totalCents(totalCents).commissionCents(commissionCents)
                .status(PurchaseStatus.PENDING).build();
        for (PurchaseItem item : items) {
            purchase.addItem(item);
        }
        return purchase;
    }

    private Copayment pendingCopayment(Purchase purchase, long keysUsed, long keysValueCents, long cashAmountCents) {
        return Copayment.builder()
                .id(UUID.randomUUID())
                .purchase(purchase)
                .consumer(purchase.getConsumer())
                .status(CopaymentStatus.PENDING)
                .keysUsed(keysUsed)
                .keysValueCents(keysValueCents)
                .cashAmountCents(cashAmountCents)
                .totalAmountCents(cashAmountCents + keysValueCents)
                .build();
    }

    private WompiTransaction wompiTx(WompiTransactionStatus status, String reference) {
        return WompiTransaction.builder().id(UUID.randomUUID()).reference(reference).status(status).build();
    }

    @Nested
    @DisplayName("handleWompiResult — APPROVED")
    class Approved {

        @Test
        @DisplayName("copago 100% en efectivo aprobado: mueve el efectivo a payouts pendientes, retiene comisión y entrega los códigos")
        void allCashApproved_movesCashRetainsCommissionAndDeliversCodes() {
            Product product = new Product();
            product.setName("Netflix");
            PurchaseItem item = itemWithStock(product);
            Purchase purchase = purchaseWithItems(100_000L, 10_000L, item);
            Copayment copayment = pendingCopayment(purchase, 0L, 0L, 100_000L);
            WompiTransaction tx = wompiTx(WompiTransactionStatus.APPROVED, purchase.getReferenceId());

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(copaymentRepository.findByPurchaseReferenceIdWithDetails(tx.getReference()))
                    .thenReturn(Optional.of(copayment));

            service.handleWompiResult(tx.getId());

            verify(treasuryService, never()).convertKeysToPayoutPending(anyLong(), any());
            verify(treasuryService).moveCashToPayoutPending(100_000L, copayment.getId());
            verify(treasuryService).retainCommission(10_000L, copayment.getId(), "COPAYMENT");
            assertThat(item.getStatus()).isEqualTo(PurchaseItemStatus.DELIVERED);
            assertThat(item.getAssignedProductStock().getStatus())
                    .isEqualTo(com.verygana2.models.enums.marketplace.StockStatus.SOLD);
            assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.COMPLETED);
            assertThat(copayment.getStatus()).isEqualTo(CopaymentStatus.COMPLETED);
            verify(eventPublisher).publishEvent(any(com.verygana2.event.XpAwardRequestedEvent.class));
        }

        @Test
        @DisplayName("copago con llaves aprobado: confirma el débito de llaves y mueve su valor a payouts pendientes")
        void withKeysApproved_confirmsKeyDebitAndMovesValue() {
            Product product = new Product();
            product.setName("Spotify");
            PurchaseItem item = itemWithStock(product);
            Purchase purchase = purchaseWithItems(100_000L, 5_000L, item);
            Copayment copayment = pendingCopayment(purchase, 50L, 50_000L, 50_000L);
            WompiTransaction tx = wompiTx(WompiTransactionStatus.APPROVED, purchase.getReferenceId());

            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(0L).blockedPurchaseKeysCents(50_000L).build();

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(copaymentRepository.findByPurchaseReferenceIdWithDetails(tx.getReference()))
                    .thenReturn(Optional.of(copayment));
            when(keyWalletRepository.findByConsumerId(1L)).thenReturn(Optional.of(wallet));

            service.handleWompiResult(tx.getId());

            assertThat(wallet.getBlockedPurchaseKeysCents()).isZero(); // confirmado, ya no bloqueado
            verify(keyTransactionRepository).save(any());
            verify(treasuryService).convertKeysToPayoutPending(50_000L, copayment.getId());
            verify(treasuryService).moveCashToPayoutPending(50_000L, copayment.getId());
        }

        @Test
        @DisplayName("comisión en cero: no llama a retainCommission")
        void zeroCommission_doesNotRetainCommission() {
            Product product = new Product();
            PurchaseItem item = itemWithStock(product);
            Purchase purchase = purchaseWithItems(100_000L, 0L, item);
            Copayment copayment = pendingCopayment(purchase, 0L, 0L, 100_000L);
            WompiTransaction tx = wompiTx(WompiTransactionStatus.APPROVED, purchase.getReferenceId());

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(copaymentRepository.findByPurchaseReferenceIdWithDetails(tx.getReference()))
                    .thenReturn(Optional.of(copayment));

            service.handleWompiResult(tx.getId());

            verify(treasuryService, never()).retainCommission(anyLong(), any(), any());
        }

        @Test
        @DisplayName("falla el envío de tickets de rifa: no revierte el pago (se captura y sigue)")
        void ticketDeliveryFailure_doesNotRollbackPayment() {
            Product product = new Product();
            PurchaseItem item = itemWithStock(product);
            Purchase purchase = purchaseWithItems(100_000L, 0L, item);
            Copayment copayment = pendingCopayment(purchase, 0L, 0L, 100_000L);
            WompiTransaction tx = wompiTx(WompiTransactionStatus.APPROVED, purchase.getReferenceId());

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(copaymentRepository.findByPurchaseReferenceIdWithDetails(tx.getReference()))
                    .thenReturn(Optional.of(copayment));
            when(ticketDeliveryService.processTicketEarningForPurchase(any(), any(), any()))
                    .thenThrow(new RuntimeException("rifas caído"));

            service.handleWompiResult(tx.getId());

            assertThat(copayment.getStatus()).isEqualTo(CopaymentStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("handleWompiResult — DECLINED / ERROR")
    class Declined {

        @Test
        @DisplayName("copago rechazado: libera el stock reservado y marca el copago FAILED con el motivo")
        void declined_releasesStockAndMarksFailed() {
            Product product = new Product();
            PurchaseItem item = itemWithStock(product);
            ProductStock stock = item.getAssignedProductStock(); // capturado antes: el servicio limpia el FK del item
            Purchase purchase = purchaseWithItems(100_000L, 10_000L, item);
            Copayment copayment = pendingCopayment(purchase, 0L, 0L, 100_000L);
            WompiTransaction tx = wompiTx(WompiTransactionStatus.DECLINED, purchase.getReferenceId());

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(copaymentRepository.findByPurchaseReferenceIdWithDetails(tx.getReference()))
                    .thenReturn(Optional.of(copayment));

            service.handleWompiResult(tx.getId());

            assertThat(stock.getStatus())
                    .isEqualTo(com.verygana2.models.enums.marketplace.StockStatus.AVAILABLE);
            assertThat(item.getStatus()).isEqualTo(PurchaseItemStatus.CANCELLED);
            assertThat(item.getAssignedProductStock()).isNull(); // FK limpiado antes de liberar el stock
            assertThat(copayment.getStatus()).isEqualTo(CopaymentStatus.FAILED);
            assertThat(copayment.getFailureReason()).isEqualTo("DECLINED");
            verify(treasuryService, never()).moveCashToPayoutPending(anyLong(), any());
        }

        @Test
        @DisplayName("rechazado con llaves usadas: las devuelve al saldo disponible")
        void declinedWithKeys_releasesKeysBackToBalance() {
            Product product = new Product();
            PurchaseItem item = itemWithStock(product);
            Purchase purchase = purchaseWithItems(100_000L, 0L, item);
            Copayment copayment = pendingCopayment(purchase, 50L, 50_000L, 50_000L);
            WompiTransaction tx = wompiTx(WompiTransactionStatus.DECLINED, purchase.getReferenceId());

            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(0L).blockedPurchaseKeysCents(50_000L).build();

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(copaymentRepository.findByPurchaseReferenceIdWithDetails(tx.getReference()))
                    .thenReturn(Optional.of(copayment));
            when(keyWalletRepository.findByConsumerId(1L)).thenReturn(Optional.of(wallet));

            service.handleWompiResult(tx.getId());

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(50_000L);
            assertThat(wallet.getBlockedPurchaseKeysCents()).isZero();
            assertThat(copayment.getKeysRefundedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("handleWompiResult — validaciones e idempotencia")
    class Validations {

        @Test
        @DisplayName("WompiTransaction inexistente: lanza IllegalArgumentException")
        void unknownWompiTransaction_throwsIllegalArgumentException() {
            UUID id = UUID.randomUUID();
            when(wompiTransactionRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.handleWompiResult(id)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Copayment no encontrado para la referencia: lanza IllegalArgumentException")
        void unknownCopayment_throwsIllegalArgumentException() {
            WompiTransaction tx = wompiTx(WompiTransactionStatus.APPROVED, "REF-404");
            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(copaymentRepository.findByPurchaseReferenceIdWithDetails("REF-404")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.handleWompiResult(tx.getId())).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("webhook reenviado (copago ya no está PENDING): no reprocesa nada")
        void alreadyProcessed_doesNothing() {
            Purchase purchase = purchaseWithItems(100_000L, 0L);
            Copayment alreadyCompleted = pendingCopayment(purchase, 0L, 0L, 100_000L);
            alreadyCompleted.setStatus(CopaymentStatus.COMPLETED);
            WompiTransaction tx = wompiTx(WompiTransactionStatus.APPROVED, purchase.getReferenceId());

            when(wompiTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
            when(copaymentRepository.findByPurchaseReferenceIdWithDetails(tx.getReference()))
                    .thenReturn(Optional.of(alreadyCompleted));

            service.handleWompiResult(tx.getId());

            verify(treasuryService, never()).moveCashToPayoutPending(anyLong(), any());
            verify(copaymentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("expireStale")
    class ExpireStale {

        @Test
        @DisplayName("sin copagos PENDING vencidos: no hace nada")
        void noStaleCopayments_doesNothing() {
            when(copaymentRepository.findExpiredPending(eq(CopaymentStatus.PENDING), any())).thenReturn(List.of());

            service.expireStale(15);

            verify(copaymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("copago PENDING vencido: lo marca FAILED con motivo PAYMENT_SESSION_EXPIRED y libera el stock")
        void staleCopayment_marksExpiredAndReleasesStock() {
            Product product = new Product();
            PurchaseItem item = itemWithStock(product);
            Purchase purchase = purchaseWithItems(100_000L, 0L, item);
            Copayment stale = pendingCopayment(purchase, 0L, 0L, 100_000L);

            when(copaymentRepository.findExpiredPending(eq(CopaymentStatus.PENDING), any()))
                    .thenReturn(List.of(stale));

            service.expireStale(15);

            assertThat(stale.getStatus()).isEqualTo(CopaymentStatus.FAILED);
            assertThat(stale.getFailureReason()).isEqualTo("PAYMENT_SESSION_EXPIRED");
            assertThat(item.getAssignedProductStock()).isNull();
            verify(copaymentRepository).save(stale);
            verify(purchaseRepository).save(purchase);
        }

        @Test
        @DisplayName("copago vencido pero Wompi confirma que sí se aprobó (webhook nunca llegó): lo completa en vez de expirarlo")
        void staleCopayment_reconciledAsApprovedByWompi_completesInsteadOfExpiring() {
            Product product = new Product();
            PurchaseItem item = itemWithStock(product);
            Purchase purchase = purchaseWithItems(100_000L, 10_000L, item);
            purchase.setReferenceId("ref-reconcile-123");
            Copayment stale = pendingCopayment(purchase, 0L, 0L, 100_000L);

            when(copaymentRepository.findExpiredPending(eq(CopaymentStatus.PENDING), any()))
                    .thenReturn(List.of(stale));

            WompiTransactionData wompiData = new WompiTransactionData();
            wompiData.setId("real-wompi-id-999");
            wompiData.setStatus("APPROVED");
            wompiData.setReference("ref-reconcile-123");
            wompiData.setCreatedAt("2026-07-14T18:00:00.000Z");
            when(wompiService.reconcileByReference("ref-reconcile-123")).thenReturn(Optional.of(wompiData));

            WompiTransaction updatedTx = wompiTx(WompiTransactionStatus.APPROVED, "ref-reconcile-123");
            when(wompiService.updateTransactionFromWebhook(eq("real-wompi-id-999"), eq("ref-reconcile-123"),
                    eq("APPROVED"), anyString(), any())).thenReturn(updatedTx);

            when(wompiTransactionRepository.findById(updatedTx.getId())).thenReturn(Optional.of(updatedTx));
            when(copaymentRepository.findByPurchaseReferenceIdWithDetails("ref-reconcile-123"))
                    .thenReturn(Optional.of(stale));

            service.expireStale(15);

            assertThat(stale.getStatus()).isEqualTo(CopaymentStatus.COMPLETED);
            assertThat(stale.getFailureReason()).isNull();
            assertThat(item.getStatus()).isEqualTo(PurchaseItemStatus.DELIVERED);
            verify(treasuryService).moveCashToPayoutPending(100_000L, stale.getId());
            verify(treasuryService).retainCommission(10_000L, stale.getId(), "COPAYMENT");
        }

        @Test
        @DisplayName("un copago con error inesperado no detiene el procesamiento de los demás")
        void oneFailure_doesNotStopProcessingOthers() {
            // Copayment sin Purchase asociada (null) para forzar un error interno en el primero.
            Copayment broken = Copayment.builder().id(UUID.randomUUID()).purchase(null)
                    .status(CopaymentStatus.PENDING).build();

            Product product = new Product();
            PurchaseItem item = itemWithStock(product);
            Purchase purchase2 = purchaseWithItems(50_000L, 0L, item);
            Copayment healthy = pendingCopayment(purchase2, 0L, 0L, 50_000L);

            when(copaymentRepository.findExpiredPending(eq(CopaymentStatus.PENDING), any()))
                    .thenReturn(List.of(broken, healthy));

            service.expireStale(15);

            assertThat(healthy.getStatus()).isEqualTo(CopaymentStatus.FAILED);
            verify(copaymentRepository).save(healthy);
        }
    }
}
