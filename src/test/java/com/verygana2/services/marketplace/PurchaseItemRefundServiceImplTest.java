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
import com.verygana2.repositories.finance.KeyTransactionRepository;
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
    @Mock private CopaymentRepository copaymentRepository;
    @Mock private KeyWalletRepository keyWalletRepository;
    @Mock private KeyTransactionRepository keyTransactionRepository;
    @Mock private PurchaseItemCashRefundRepository purchaseItemCashRefundRepository;

    private PurchaseItemRefundServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PurchaseItemRefundServiceImpl(purchaseItemRepository, productStockRepository, treasuryService,
                copaymentRepository, keyWalletRepository, keyTransactionRepository, purchaseItemCashRefundRepository);
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
            verifyNoInteractions(treasuryService, copaymentRepository, keyWalletRepository, purchaseItemRepository,
                    purchaseItemCashRefundRepository);
        }

        @Test
        @DisplayName("sin llaves: revierte tesorería (todo como efectivo), marca REFUNDED, crea el reembolso en efectivo pendiente, y el stock NO vuelve a AVAILABLE (código ya fue revelado)")
        void withoutKeys_reversesTreasuryCreatesCashRefundAndMarksRefunded() {
            PurchaseItem item = itemWithStock(100_000L, 100_000L, 10_000L, 90_000L, PurchaseItemStatus.CLAIMED);
            Copayment copayment = copayment(consumer(1L), 0L);
            when(copaymentRepository.findByPurchaseId(50L)).thenReturn(Optional.of(copayment));

            PurchaseItemCashRefund result = service.refund(item, MarketplaceIssueReason.NOT_DELIVERED, null);

            verify(treasuryService).reversePurchaseItemForRefund(10_000L, 0L, 100_000L, copayment.getId());
            assertThat(item.getStatus()).isEqualTo(PurchaseItemStatus.REFUNDED);
            assertThat(item.getAssignedProductStock().getStatus()).isEqualTo(StockStatus.SOLD); // sin tocar
            verifyNoInteractions(keyWalletRepository);
            verify(purchaseItemRepository).save(item);

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
            PurchaseItem item = itemWithStock(100_000L, 100_000L, 10_000L, 90_000L, PurchaseItemStatus.CLAIMED);
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
            PurchaseItem item = itemWithStock(100_000L, 50_000L, 5_000L, 45_000L, PurchaseItemStatus.CLAIMED);
            ConsumerDetails consumerDetails = consumer(1L);
            Copayment copayment = copayment(consumerDetails, 40_000L);
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(0L).build();

            when(copaymentRepository.findByPurchaseId(50L)).thenReturn(Optional.of(copayment));
            when(keyWalletRepository.findByConsumerId(1L)).thenReturn(Optional.of(wallet));

            service.refund(item, MarketplaceIssueReason.NOT_DELIVERED, null);

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(20_000L);
            verify(keyWalletRepository).save(wallet);
            verify(keyTransactionRepository).save(any());
            verify(treasuryService).reversePurchaseItemForRefund(5_000L, 20_000L, 30_000L, copayment.getId());

            var captor = org.mockito.ArgumentCaptor.forClass(PurchaseItemCashRefund.class);
            verify(purchaseItemCashRefundRepository).save(captor.capture());
            assertThat(captor.getValue().getAmountCents()).isEqualTo(30_000L);
        }

        @Test
        @DisplayName("compra pagada 100% con llaves: no crea reembolso en efectivo")
        void fullyPaidWithKeys_doesNotCreateCashRefund() {
            PurchaseItem item = itemWithStock(50_000L, 50_000L, 5_000L, 45_000L, PurchaseItemStatus.CLAIMED);
            ConsumerDetails consumerDetails = consumer(1L);
            Copayment copayment = copayment(consumerDetails, 50_000L);
            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(0L).build();

            when(copaymentRepository.findByPurchaseId(50L)).thenReturn(Optional.of(copayment));
            when(keyWalletRepository.findByConsumerId(1L)).thenReturn(Optional.of(wallet));

            service.refund(item, MarketplaceIssueReason.NOT_DELIVERED, null);

            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(50_000L);
            verifyNoInteractions(purchaseItemCashRefundRepository);
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

            verify(treasuryService).reversePurchaseItemForRefund(10_000L, 0L, 100_000L, copayment.getId());
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
