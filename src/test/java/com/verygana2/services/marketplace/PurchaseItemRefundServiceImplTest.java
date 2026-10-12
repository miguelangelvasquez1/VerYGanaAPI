package com.verygana2.services.marketplace;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.services.interfaces.finance.KeyLedgerService;
import com.verygana2.testsupport.KeyLedgerStubs;
import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.enums.pqrs.MarketplaceIssueReason;
import com.verygana2.models.finance.Copayment;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.finance.PurchaseItemCashRefund;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.finance.CopaymentRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.repositories.finance.PurchaseItemCashRefundRepository;
import com.verygana2.repositories.marketplace.ProductStockRepository;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;
import com.verygana2.services.interfaces.finance.TreasuryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PurchaseItemRefundServiceImpl}: la mecánica interna del
 * reembolso de un PurchaseItem (tesorería + llaves + estado del ítem/stock).
 * No cubre el reverso del cobro en Wompi — ese paso queda manual por ahora.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseItemRefundServiceImpl")
class PurchaseItemRefundServiceImplTest {

    @Mock private PurchaseItemRepository purchaseItemRepository;
    @Mock private ProductStockRepository productStockRepository;
    @Mock private TreasuryService treasuryService;
    @Mock private com.verygana2.services.interfaces.finance.ProsperityService prosperityService;
    @Mock private CopaymentRepository copaymentRepository;
    @Mock private KeyWalletRepository keyWalletRepository;
    @Mock private KeyLedgerService keyLedgerService;
    @Mock private com.verygana2.services.interfaces.marketplace.KeyCoveragePromotionService keyCoveragePromotionService;
    @Mock private com.verygana2.services.interfaces.finance.KeyWalletService keyWalletService;
    @Mock private PurchaseItemCashRefundRepository purchaseItemCashRefundRepository;

    private PurchaseItemRefundServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PurchaseItemRefundServiceImpl(purchaseItemRepository, productStockRepository, treasuryService,
                prosperityService, copaymentRepository, keyWalletRepository, keyLedgerService, keyWalletService,
                purchaseItemCashRefundRepository, keyCoveragePromotionService);
        KeyLedgerStubs.applyToWallet(keyLedgerService);
        org.mockito.Mockito.lenient().when(keyCoveragePromotionService.restoreForRefund(any()))
                .thenReturn(com.verygana2.services.interfaces.marketplace.KeyCoveragePromotionService.ExcessRefund.none());
    }

    private ConsumerDetails consumer(Long id) {
        ConsumerDetails consumer = new ConsumerDetails();
        consumer.setId(id);
        return consumer;
    }

    private PurchaseItem itemWithStock(long purchaseTotalCents, long subtotalCents, long commissionCents,
            long netToCommercialCents, PurchaseItemStatus status) {
        Product product = new Product();
        ProductStock stock = ProductStock.builder().product(product).status(StockStatus.SOLD).build();
        PurchaseItem item = new PurchaseItem();
        item.setId(7L);
        item.setProduct(product);
        item.setAssignedProductStock(stock);
        item.setSubtotalCents(subtotalCents);
        item.setCommissionCents(commissionCents);
        item.setNetToCommercialCents(netToCommercialCents);
        item.setStatus(status);
        Purchase purchase = Purchase.builder().id(50L).totalCents(purchaseTotalCents).build();
        item.setPurchase(purchase);
        return item;
    }

    private Copayment copayment(ConsumerDetails consumer, long keysValueCents) {
        return Copayment.builder().id(UUID.randomUUID()).consumer(consumer).keysValueCents(keysValueCents).build();
    }

    @Nested
    @DisplayName("refund")
    class Refund {

        @Test
        @DisplayName("ítem ya REFUNDED: idempotente, no toca tesorería ni repositorios")
        void alreadyRefunded_isIdempotent() {
            PurchaseItem item = itemWithStock(100_000L, 100_000L, 10_000L, 90_000L, PurchaseItemStatus.REFUNDED);

            PurchaseItemCashRefund result = service.refund(item, MarketplaceIssueReason.NOT_DELIVERED, null);

            assertThat(result).isNull();
            verifyNoInteractions(treasuryService, prosperityService, copaymentRepository, keyWalletRepository,
                    purchaseItemRepository, purchaseItemCashRefundRepository);
        }

        @Test
        @DisplayName("sin llaves: revierte tesorería (todo como efectivo), crea el reembolso en efectivo pendiente, el ítem se queda IN_REVIEW (esperando markPaid), y el stock NO vuelve a AVAILABLE (código ya fue revelado)")
        void withoutKeys_reversesTreasuryCreatesCashRefundAndStaysInReview() {
            PurchaseItem item = itemWithStock(100_000L, 100_000L, 10_000L, 90_000L, PurchaseItemStatus.IN_REVIEW);
            Copayment copayment = copayment(consumer(1L), 0L);
            when(copaymentRepository.findByPurchaseId(50L)).thenReturn(Optional.of(copayment));

            PurchaseItemCashRefund result = service.refund(item, MarketplaceIssueReason.NOT_DELIVERED, null);

            // Prosperidad antes que tesorería: es el orden de bloqueo del depósito y del copago.
            org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(prosperityService, treasuryService);
            inOrder.verify(prosperityService).reintegrateRefund(item);
            inOrder.verify(treasuryService).reversePurchaseItemForRefund(10_000L, 0L, 0L, 100_000L, copayment.getId());
            // Hay porción en efectivo pendiente de pago manual: el ítem NO pasa a
            // REFUNDED todavía — eso solo ocurre en CashRefundServiceImpl.markPaid.
            assertThat(item.getStatus()).isEqualTo(PurchaseItemStatus.IN_REVIEW);
            assertThat(item.getAssignedProductStock().getStatus()).isEqualTo(StockStatus.SOLD); // sin tocar
            verifyNoInteractions(keyWalletRepository);
            verify(purchaseItemRepository, never()).save(item);

            var captor = org.mockito.ArgumentCaptor.forClass(PurchaseItemCashRefund.class);
            verify(purchaseItemCashRefundRepository).save(captor.capture());
            assertThat(captor.getValue().getAmountCents()).isEqualTo(100_000L);
            assertThat(captor.getValue().getPurchaseItem()).isSameAs(item);
            assertThat(result).isSameAs(captor.getValue());
        }

        @Test
        @DisplayName("motivo CODE_INVALID: además marca el stock como INVALID")
        void codeInvalidReason_marksStockInvalid() {
            PurchaseItem item = itemWithStock(100_000L, 100_000L, 10_000L, 90_000L, PurchaseItemStatus.PENDING);
            Copayment copayment = copayment(consumer(1L), 0L);
            when(copaymentRepository.findByPurchaseId(50L)).thenReturn(Optional.of(copayment));

            service.refund(item, MarketplaceIssueReason.CODE_INVALID, null);

            assertThat(item.getAssignedProductStock().getStatus()).isEqualTo(StockStatus.INVALID);
            verify(productStockRepository).save(item.getAssignedProductStock());
        }

        @Test
        @DisplayName("con PQRS vinculado: el reembolso en efectivo creado queda enlazado a ese PQRS")
        void withLinkedPqrs_cashRefundReferencesIt() {
            PurchaseItem item = itemWithStock(100_000L, 100_000L, 10_000L, 90_000L, PurchaseItemStatus.IN_REVIEW);
            Copayment copayment = copayment(consumer(1L), 0L);
            when(copaymentRepository.findByPurchaseId(50L)).thenReturn(Optional.of(copayment));
            Pqrs pqrs = Pqrs.builder().id(9L).build();

            PurchaseItemCashRefund result = service.refund(item, MarketplaceIssueReason.NOT_DELIVERED, pqrs);

            assertThat(result).isNotNull();
            assertThat(result.getPqrs()).isSameAs(pqrs);
        }

        @Test
        @DisplayName("compra pagada parcialmente con llaves: acredita de vuelta la porción proporcional al KeyWallet y crea el reembolso en efectivo solo por el resto")
        void withKeys_creditsProportionalShareBackToWalletAndRefundsOnlyCashRemainder() {
            // Compra de 100.000 total, este ítem es la mitad (50.000), copayment usó 40.000 en llaves
            // → porción de llaves del ítem = 40.000 * (50.000/100.000) = 20.000
            // → porción en efectivo del ítem = (5.000+45.000) - 20.000 = 30.000
            PurchaseItem item = itemWithStock(100_000L, 50_000L, 5_000L, 45_000L, PurchaseItemStatus.IN_REVIEW);
            ConsumerDetails consumerDetails = consumer(1L);
            Copayment copayment = copayment(consumerDetails, 40_000L);
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(0L).build();

            when(copaymentRepository.findByPurchaseId(50L)).thenReturn(Optional.of(copayment));
            when(keyWalletRepository.findByConsumerIdForUpdate(1L)).thenReturn(Optional.of(wallet));

            service.refund(item, MarketplaceIssueReason.NOT_DELIVERED, null);

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(20_000L);
            verify(keyLedgerService).refundPurchaseItem(
                    org.mockito.ArgumentMatchers.eq(wallet), org.mockito.ArgumentMatchers.eq(20_000L),
                    org.mockito.ArgumentMatchers.eq(copayment.getId()), org.mockito.ArgumentMatchers.eq(item.getId()),
                    any());
            verify(treasuryService).reversePurchaseItemForRefund(5_000L, 0L, 20_000L, 30_000L, copayment.getId());

            var captor = org.mockito.ArgumentCaptor.forClass(PurchaseItemCashRefund.class);
            verify(purchaseItemCashRefundRepository).save(captor.capture());
            assertThat(captor.getValue().getAmountCents()).isEqualTo(30_000L);
        }

        @Test
        @DisplayName("devuelve exactamente la Porción en Llaves registrada en el ítem, no un prorrateo del copago")
        void refundsExactKeysPortionOfTheItem() {
            // Mismo copago que el caso prorrateado (40.000 en llaves, ítem = mitad de la compra →
            // 20.000 por peso), pero el ítem registró que a él le tocaron 12.345.
            PurchaseItem item = itemWithStock(100_000L, 50_000L, 5_000L, 45_000L, PurchaseItemStatus.IN_REVIEW);
            item.setKeysValueCents(12_345L);
            ConsumerDetails consumerDetails = consumer(1L);
            Copayment copayment = copayment(consumerDetails, 40_000L);
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(0L).build();

            when(copaymentRepository.findByPurchaseId(50L)).thenReturn(Optional.of(copayment));
            when(keyWalletRepository.findByConsumerIdForUpdate(1L)).thenReturn(Optional.of(wallet));

            service.refund(item, MarketplaceIssueReason.NOT_DELIVERED, null);

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(12_345L);
            verify(treasuryService).reversePurchaseItemForRefund(5_000L, 0L, 12_345L, 37_655L, copayment.getId());
        }

        @Test
        @DisplayName("ítem comprado con promoción especial: todo vuelve a su sitio, incluido lo que puso el Fondo")
        void refundOfPromotedItemGivesTheExcessBackToTheFund() {
            // $500.000 al 70 %: $350.000 en llaves, de los cuales $100.000 los puso el Fondo.
            PurchaseItem item = itemWithStock(50_000_000L, 50_000_000L, 7_500_000L, 42_500_000L,
                    PurchaseItemStatus.IN_REVIEW);
            item.setKeysValueCents(35_000_000L);
            item.setKeyCoveragePromotionId(1L);
            item.setPromotionExcessCents(10_000_000L);
            Copayment copayment = copayment(consumer(1L), 35_000_000L);
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(0L).build();
            UUID promotionRef = UUID.randomUUID();
            when(copaymentRepository.findByPurchaseId(50L)).thenReturn(Optional.of(copayment));
            when(keyWalletRepository.findByConsumerIdForUpdate(1L)).thenReturn(Optional.of(wallet));
            when(keyCoveragePromotionService.restoreForRefund(item)).thenReturn(
                    new com.verygana2.services.interfaces.marketplace.KeyCoveragePromotionService.ExcessRefund(
                            10_000_000L, true, promotionRef));

            service.refund(item, MarketplaceIssueReason.NOT_DELIVERED, null);

            // El consumidor recupera sus 350.000 en llaves y los 150.000 en dinero.
            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(35_000_000L);
            // KEYS_RESERVE recupera las llaves completas y le devuelve al Fondo su parte.
            var order = org.mockito.Mockito.inOrder(keyCoveragePromotionService, treasuryService);
            order.verify(keyCoveragePromotionService).restoreForRefund(item);
            order.verify(treasuryService).reversePurchaseItemForRefund(
                    7_500_000L, 0L, 35_000_000L, 15_000_000L, copayment.getId());
            order.verify(treasuryService).refundPromotionExcess(10_000_000L, true, promotionRef);
        }

        @Test
        @DisplayName("ítem sin promoción: no se devuelve nada al Fondo")
        void refundOfOrdinaryItemDoesNotTouchTheFund() {
            PurchaseItem item = itemWithStock(100_000L, 50_000L, 5_000L, 45_000L, PurchaseItemStatus.IN_REVIEW);
            item.setKeysValueCents(0L);
            when(copaymentRepository.findByPurchaseId(50L)).thenReturn(Optional.of(copayment(consumer(1L), 0L)));

            service.refund(item, MarketplaceIssueReason.NOT_DELIVERED, null);

            verify(treasuryService, never()).refundPromotionExcess(
                    org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyBoolean(), any());
        }

        @Test
        @DisplayName("ítem que se pagó sin llaves dentro de una compra que sí las usó: no devuelve llaves")
        void itemPaidWithoutKeysRefundsNoKeys() {
            PurchaseItem item = itemWithStock(100_000L, 50_000L, 5_000L, 45_000L, PurchaseItemStatus.IN_REVIEW);
            item.setKeysValueCents(0L);
            Copayment copayment = copayment(consumer(1L), 40_000L);
            when(copaymentRepository.findByPurchaseId(50L)).thenReturn(Optional.of(copayment));

            service.refund(item, MarketplaceIssueReason.NOT_DELIVERED, null);

            verifyNoInteractions(keyWalletRepository, keyLedgerService);
            verify(treasuryService).reversePurchaseItemForRefund(5_000L, 0L, 0L, 50_000L, copayment.getId());
        }

        @Test
        @DisplayName("caso límite artificial, 100% cubierto por llaves (en la práctica maxKeysPct nunca llega a 100 — ver Product.maxKeysPct): no crea reembolso en efectivo; refund() no toca el status, sigue IN_REVIEW")
        void fullyPaidWithKeys_doesNotCreateCashRefundAndDoesNotTouchStatus() {
            PurchaseItem item = itemWithStock(50_000L, 50_000L, 5_000L, 45_000L, PurchaseItemStatus.IN_REVIEW);
            ConsumerDetails consumerDetails = consumer(1L);
            Copayment copayment = copayment(consumerDetails, 50_000L);
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(0L).build();

            when(copaymentRepository.findByPurchaseId(50L)).thenReturn(Optional.of(copayment));
            when(keyWalletRepository.findByConsumerIdForUpdate(1L)).thenReturn(Optional.of(wallet));

            service.refund(item, MarketplaceIssueReason.NOT_DELIVERED, null);

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(50_000L);
            verifyNoInteractions(purchaseItemCashRefundRepository);
            // refund() ya no toca el status del ítem directamente en ningún caso —
            // eso queda exclusivamente en manos de CashRefundServiceImpl.markPaid.
            assertThat(item.getStatus()).isEqualTo(PurchaseItemStatus.IN_REVIEW);
            verify(purchaseItemRepository, never()).save(item);
        }

        @Test
        @DisplayName("Copayment no encontrado: lanza IllegalStateException")
        void copaymentNotFound_throwsIllegalStateException() {
            PurchaseItem item = itemWithStock(100_000L, 100_000L, 10_000L, 90_000L, PurchaseItemStatus.CLAIMED);
            when(copaymentRepository.findByPurchaseId(50L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.refund(item, MarketplaceIssueReason.NOT_DELIVERED, null))
                    .isInstanceOf(IllegalStateException.class);

            verify(purchaseItemRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("expireUnclaimed")
    class ExpireUnclaimed {

        @Test
        @DisplayName("ítem ya EXPIRED_UNCLAIMED: idempotente, no toca tesorería ni repositorios")
        void alreadyExpired_isIdempotent() {
            PurchaseItem item = itemWithStock(100_000L, 100_000L, 10_000L, 90_000L, PurchaseItemStatus.EXPIRED_UNCLAIMED);

            service.expireUnclaimed(item);

            verifyNoInteractions(treasuryService, copaymentRepository, keyWalletRepository, purchaseItemRepository,
                    purchaseItemCashRefundRepository);
        }

        @Test
        @DisplayName("vence un ítem PENDING: revierte tesorería, crea el reembolso en efectivo y marca EXPIRED_UNCLAIMED sin tocar el stock")
        void pendingItem_reversesTreasuryAndMarksExpired() {
            PurchaseItem item = itemWithStock(100_000L, 100_000L, 10_000L, 90_000L, PurchaseItemStatus.PENDING);
            Copayment copayment = copayment(consumer(1L), 0L);
            when(copaymentRepository.findByPurchaseId(50L)).thenReturn(Optional.of(copayment));

            service.expireUnclaimed(item);

            verify(treasuryService).reversePurchaseItemForRefund(10_000L, 0L, 0L, 100_000L, copayment.getId());
            assertThat(item.getStatus()).isEqualTo(PurchaseItemStatus.EXPIRED_UNCLAIMED);
            // a diferencia de refund(CODE_INVALID), expireUnclaimed nunca marca el stock INVALID
            assertThat(item.getAssignedProductStock().getStatus()).isEqualTo(StockStatus.SOLD);
            verifyNoInteractions(productStockRepository);
            verify(purchaseItemRepository).save(item);

            var captor = org.mockito.ArgumentCaptor.forClass(PurchaseItemCashRefund.class);
            verify(purchaseItemCashRefundRepository).save(captor.capture());
            assertThat(captor.getValue().getAmountCents()).isEqualTo(100_000L);
        }
    }
}
