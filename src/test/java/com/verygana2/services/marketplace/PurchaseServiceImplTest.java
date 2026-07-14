package com.verygana2.services.marketplace;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.dtos.purchase.requests.CreatePurchaseItemRequestDTO;
import com.verygana2.dtos.purchase.requests.CreatePurchaseRequestDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.exceptions.InsufficientStockException;
import com.verygana2.exceptions.ProductNotAvailableException;
import com.verygana2.mappers.marketplace.PurchaseMapper;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.finance.CopaymentRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.repositories.finance.WompiTransactionRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.repositories.marketplace.ProductStockRepository;
import com.verygana2.repositories.marketplace.PurchaseRepository;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.marketplace.ProductService;
import com.verygana2.services.wompi.WompiService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PurchaseServiceImpl}: iniciar una compra reserva stock,
 * calcula comisión según el plan del comercial, valida cuánto puede pagarse
 * con llaves vs. efectivo, y arranca el checkout de Wompi. 1 llave = 10 COP
 * = 1.000 centavos (KEY_VALUE), inyectado vía @Value.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseServiceImpl")
class PurchaseServiceImplTest {

    private static final long KEY_VALUE_CENTS = 1000L;

    @Mock private PurchaseRepository purchaseRepository;
    @Mock private ProductService productService;
    @Mock private ProductRepository productRepository;
    @Mock private ProductStockRepository productStockRepository;
    @Mock private ConsumerDetailsService consumerDetailsService;
    @Mock private KeyWalletRepository keyWalletRepository;
    @Mock private CopaymentRepository copaymentRepository;
    @Mock private WompiService wompiService;
    @Mock private WompiTransactionRepository wompiTransactionRepository;
    @Mock private PurchaseMapper purchaseMapper;

    private PurchaseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PurchaseServiceImpl(purchaseRepository, productService, productRepository,
                productStockRepository, consumerDetailsService, keyWalletRepository, copaymentRepository,
                wompiService, wompiTransactionRepository, purchaseMapper);
        ReflectionTestUtils.setField(service, "KEY_VALUE", KEY_VALUE_CENTS);
    }

    private Product activeProduct(long priceCents, int maxKeysPct, int availableStock) {
        Product product = new Product();
        product.setId(1L);
        product.setName("Netflix");
        product.setStatus(ProductStatus.ACTIVE);
        product.setPriceCents(priceCents);
        product.setMaxKeysPct(maxKeysPct);

        CommercialDetails commercial = new CommercialDetails();
        Plan plan = Plan.builder().saleCommissionPct(10).build();
        commercial.setCurrentPlan(plan);
        product.setCommercial(commercial);

        List<ProductStock> stockItems = new java.util.ArrayList<>();
        for (int i = 0; i < availableStock; i++) {
            stockItems.add(ProductStock.builder().status(StockStatus.AVAILABLE).build());
        }
        product.setStockItems(stockItems);
        return product;
    }

    private ConsumerDetails consumer(Long id) {
        ConsumerDetails consumer = new ConsumerDetails();
        com.verygana2.models.User user = new com.verygana2.models.User();
        user.setId(id);
        user.setEmail("comprador@test.com");
        consumer.setUser(user);
        return consumer;
    }

    private CreatePurchaseRequestDTO requestFor(Long keysToUse, int quantity) {
        return CreatePurchaseRequestDTO.builder()
                .items(List.of(CreatePurchaseItemRequestDTO.builder().productId(1L).quantity(quantity).build()))
                .keysToUse(keysToUse)
                .build();
    }

    /** Stubs necesarios para llegar hasta la reserva de stock (addPurchaseItems). */
    private void stubUpToStockReservation(Product product) {
        when(consumerDetailsService.getConsumerById(9L)).thenReturn(consumer(9L));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productService.getById(1L)).thenReturn(product);
        when(productStockRepository.findNextAvailableForProduct(1L))
                .thenReturn(Optional.of(ProductStock.builder().status(StockStatus.AVAILABLE).build()));
    }

    /** Stubs completos hasta el checkout de Wompi, para los tests de camino feliz. */
    private void stubHappyPathCollaborators(Product product) {
        stubUpToStockReservation(product);
        when(copaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(wompiService.createCheckoutUrl(any(), any())).thenReturn(
                WompiCheckoutResponseDTO.builder().checkoutUrl("https://checkout.wompi.co/xyz").build());
        when(wompiTransactionRepository.findByReference(any()))
                .thenReturn(Optional.of(com.verygana2.models.finance.WompiTransaction.builder().build()));
    }

    @Nested
    @DisplayName("createPurchase")
    class CreatePurchase {

        @Test
        @DisplayName("100% en efectivo (keysToUse=0): reserva 1 unidad de stock y no toca el KeyWallet")
        void allCash_reservesStockWithoutTouchingWallet() {
            // $10.000 COP, 30% máximo en llaves, 1 unidad disponible.
            Product product = activeProduct(1_000_000L, 30, 1);
            stubHappyPathCollaborators(product);

            var response = service.createPurchase(9L, requestFor(0L, 1));

            assertThat(response.getCashAmountCents()).isEqualTo(1_000_000L);
            assertThat(response.getKeysValueCents()).isZero();
            assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout.wompi.co/xyz");
            verify(keyWalletRepository, never()).findByConsumerId(any());
        }

        @Test
        @DisplayName("pago mixto (con llaves): reserva las llaves solicitadas en el KeyWallet")
        void mixedPayment_reservesKeysInWallet() {
            Product product = activeProduct(1_000_000L, 30, 1); // máximo 300 llaves permitidas
            stubHappyPathCollaborators(product);

            KeyWallet wallet = KeyWallet.builder()
                    .purchaseKeysCents(500L * KEY_VALUE_CENTS)
                    .blockedPurchaseKeysCents(0L)
                    .build();
            when(keyWalletRepository.findByConsumerId(9L)).thenReturn(Optional.of(wallet));
            when(keyWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var response = service.createPurchase(9L, requestFor(100L, 1));

            assertThat(response.getKeysValueCents()).isEqualTo(100L * KEY_VALUE_CENTS);
            assertThat(response.getCashAmountCents()).isEqualTo(1_000_000L - 100L * KEY_VALUE_CENTS);
            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(400L * KEY_VALUE_CENTS); // 500 - 100 reservadas
            assertThat(wallet.getBlockedPurchaseKeysCents()).isEqualTo(100L * KEY_VALUE_CENTS);
        }

        @Test
        @DisplayName("carrito vacío: lanza IllegalArgumentException")
        void emptyItems_throwsIllegalArgumentException() {
            CreatePurchaseRequestDTO request = CreatePurchaseRequestDTO.builder().items(List.of()).build();

            assertThatThrownBy(() -> service.createPurchase(9L, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("producto no ACTIVE: lanza ProductNotAvailableException")
        void inactiveProduct_throwsProductNotAvailableException() {
            Product inactive = activeProduct(1_000_000L, 30, 1);
            inactive.setStatus(ProductStatus.PENDING);

            when(consumerDetailsService.getConsumerById(9L)).thenReturn(consumer(9L));
            when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));
            when(productService.getById(1L)).thenReturn(inactive);

            assertThatThrownBy(() -> service.createPurchase(9L, requestFor(0L, 1)))
                    .isInstanceOf(ProductNotAvailableException.class);
        }

        @Test
        @DisplayName("stock insuficiente para la cantidad pedida: lanza InsufficientStockException")
        void insufficientStock_throwsInsufficientStockException() {
            Product product = activeProduct(1_000_000L, 30, 1); // solo 1 disponible

            when(consumerDetailsService.getConsumerById(9L)).thenReturn(consumer(9L));
            when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));
            when(productService.getById(1L)).thenReturn(product);

            assertThatThrownBy(() -> service.createPurchase(9L, requestFor(0L, 2))) // pide 2
                    .isInstanceOf(InsufficientStockException.class);
        }

        @Test
        @DisplayName("keysToUse supera el máximo permitido por el producto: lanza IllegalArgumentException")
        void keysExceedMax_throwsIllegalArgumentException() {
            Product product = activeProduct(1_000_000L, 30, 1); // máximo 300 llaves permitidas
            stubUpToStockReservation(product);

            assertThatThrownBy(() -> service.createPurchase(9L, requestFor(1000L, 1))) // pide 1000, máx 300
                    .isInstanceOf(IllegalArgumentException.class);

            verify(keyWalletRepository, never()).findByConsumerId(any());
        }

        @Test
        @DisplayName("KeyWallet sin saldo suficiente: lanza InsufficientFundsException")
        void insufficientKeyBalance_throwsInsufficientFundsException() {
            Product product = activeProduct(1_000_000L, 30, 1);
            stubUpToStockReservation(product);

            KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(10L * KEY_VALUE_CENTS).build(); // insuficientes para pedir 100
            when(keyWalletRepository.findByConsumerId(9L)).thenReturn(Optional.of(wallet));

            assertThatThrownBy(() -> service.createPurchase(9L, requestFor(100L, 1)))
                    .isInstanceOf(InsufficientFundsException.class);
        }
    }

    @Nested
    @DisplayName("consultas (getPurchaseById / getByIdAndConsumerId)")
    class Queries {

        @Test
        @DisplayName("getPurchaseById con id inválido (<=0): lanza IllegalArgumentException")
        void invalidId_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.getPurchaseById(0L)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("getPurchaseById inexistente: lanza ObjectNotFoundException")
        void notFound_throwsObjectNotFoundException() {
            when(purchaseRepository.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getPurchaseById(1L)).isInstanceOf(ObjectNotFoundException.class);
        }

        @Test
        @DisplayName("getByIdAndConsumerId: compra de otro consumidor no aparece (ObjectNotFoundException)")
        void wrongConsumer_throwsObjectNotFoundException() {
            when(purchaseRepository.findByIdAndConsumerId(1L, 2L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getByIdAndConsumerId(1L, 2L))
                    .isInstanceOf(ObjectNotFoundException.class);
        }
    }
}
