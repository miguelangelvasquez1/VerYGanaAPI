package com.verygana2.services.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.purchase.requests.CreatePurchaseItemRequestDTO;
import com.verygana2.dtos.purchase.requests.CreatePurchaseRequestDTO;
import com.verygana2.dtos.purchase.responses.ConsumerPurchaseResponseDTO;
import com.verygana2.dtos.purchase.responses.InitiatePurchaseResponseDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.exceptions.InsufficientStockException;
import com.verygana2.exceptions.ProductNotAvailableException;
import com.verygana2.mappers.marketplace.PurchaseMapper;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.enums.marketplace.PurchaseStatus;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.finance.WompiTransaction;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseServiceImpl")
class PurchaseServiceImplTest {

    @Mock PurchaseRepository purchaseRepository;
    @Mock ProductService productService;
    @Mock ProductRepository productRepository;
    @Mock ProductStockRepository productStockRepository;
    @Mock ConsumerDetailsService consumerDetailsService;
    @Mock KeyWalletRepository keyWalletRepository;
    @Mock CopaymentRepository copaymentRepository;
    @Mock WompiService wompiService;
    @Mock WompiTransactionRepository wompiTransactionRepository;
    @Mock PurchaseMapper purchaseMapper;

    @InjectMocks PurchaseServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "KEY_VALUE", 1000L);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Plan plan(int commissionPct, int maxKeysPct) {
        Plan p = new Plan();
        p.setSaleCommissionPct(commissionPct);
        p.setMaxKeysPct(maxKeysPct);
        return p;
    }

    private CommercialDetails commercial(int commissionPct) {
        CommercialDetails c = new CommercialDetails();
        c.setId(1L);
        c.setCurrentPlan(plan(commissionPct, 30));
        return c;
    }

    private Product activeProduct(Long id, long priceCents, int availableStock, int maxKeysPct) {
        CommercialDetails commercial = commercial(10);
        Product p = new Product();
        p.setId(id);
        p.setName("Product-" + id);
        p.setStatus(ProductStatus.ACTIVE);
        p.setPriceCents(priceCents);
        p.setMaxKeysPct(maxKeysPct);
        p.setCommercial(commercial);
        return p;
    }

    private ConsumerDetails consumer(Long id, String email) {
        ConsumerDetails c = new ConsumerDetails();
        c.setId(id);
        com.verygana2.models.User user = new com.verygana2.models.User();
        user.setEmail(email);
        c.setUser(user);
        return c;
    }

    private ProductStock availableStock(Long id) {
        return ProductStock.builder()
                .id(id)
                .code("CODE-" + id)
                .status(StockStatus.AVAILABLE)
                .build();
    }

    private CreatePurchaseRequestDTO purchaseRequest(Long productId, int quantity, Long keysToUse) {
        CreatePurchaseItemRequestDTO item = new CreatePurchaseItemRequestDTO();
        item.setProductId(productId);
        item.setQuantity(quantity);

        CreatePurchaseRequestDTO req = new CreatePurchaseRequestDTO();
        req.setItems(List.of(item));
        req.setKeysToUse(keysToUse);
        return req;
    }

    // ─── getPurchaseById ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPurchaseById")
    class GetPurchaseById {

        @Test
        @DisplayName("throws IllegalArgumentException for null purchase ID")
        void throwsOnNullId() {
            assertThatThrownBy(() -> service.getPurchaseById(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for non-positive purchase ID")
        void throwsOnNonPositiveId() {
            assertThatThrownBy(() -> service.getPurchaseById(0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("returns Purchase when found")
        void returnsPurchase() {
            Purchase purchase = new Purchase();
            when(purchaseRepository.findById(1L)).thenReturn(Optional.of(purchase));

            Purchase result = service.getPurchaseById(1L);

            assertThat(result).isSameAs(purchase);
        }

        @Test
        @DisplayName("throws ObjectNotFoundException when not found")
        void throwsWhenNotFound() {
            when(purchaseRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPurchaseById(99L))
                    .isInstanceOf(ObjectNotFoundException.class);
        }
    }

    // ─── getByIdAndConsumerId ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getByIdAndConsumerId")
    class GetByIdAndConsumerId {

        @Test
        @DisplayName("throws IllegalArgumentException for null purchase ID")
        void throwsOnNullPurchaseId() {
            assertThatThrownBy(() -> service.getByIdAndConsumerId(null, 1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null consumer ID")
        void throwsOnNullConsumerId() {
            assertThatThrownBy(() -> service.getByIdAndConsumerId(1L, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("returns Purchase when found")
        void returnsPurchase() {
            Purchase purchase = new Purchase();
            when(purchaseRepository.findByIdAndConsumerId(1L, 2L)).thenReturn(Optional.of(purchase));

            Purchase result = service.getByIdAndConsumerId(1L, 2L);

            assertThat(result).isSameAs(purchase);
        }

        @Test
        @DisplayName("throws ObjectNotFoundException when not found")
        void throwsWhenNotFound() {
            when(purchaseRepository.findByIdAndConsumerId(1L, 99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByIdAndConsumerId(1L, 99L))
                    .isInstanceOf(ObjectNotFoundException.class);
        }
    }

    // ─── getConsumerPurchases ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getConsumerPurchases")
    class GetConsumerPurchases {

        @Test
        @DisplayName("returns paged purchases mapped to DTOs")
        void returnsMappedPurchases() {
            Purchase purchase = new Purchase();
            ConsumerPurchaseResponseDTO dto = new ConsumerPurchaseResponseDTO();
            Page<Purchase> page = new PageImpl<>(List.of(purchase));

            when(purchaseRepository.findByConsumerId(1L, PageRequest.of(0, 10))).thenReturn(page);
            when(purchaseMapper.toConsumerPurchaseResponseDTO(purchase)).thenReturn(dto);

            PagedResponse<ConsumerPurchaseResponseDTO> result = service.getConsumerPurchases(1L, PageRequest.of(0, 10));

            assertThat(result.getData()).containsExactly(dto);
        }
    }

    // ─── createPurchase ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("createPurchase")
    class CreatePurchase {

        @Test
        @DisplayName("throws IllegalArgumentException when items list is empty")
        void throwsWhenNoItems() {
            CreatePurchaseRequestDTO req = new CreatePurchaseRequestDTO();
            req.setItems(List.of());

            assertThatThrownBy(() -> service.createPurchase(1L, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one item");
        }

        @Test
        @DisplayName("throws ProductNotAvailableException when product is not ACTIVE")
        void throwsWhenProductNotActive() {
            ConsumerDetails c = consumer(1L, "test@example.com");
            Product inactiveProduct = activeProduct(10L, 10_000L, 5, 30);
            inactiveProduct.setStatus(ProductStatus.PENDING);

            Purchase savedPurchase = new Purchase();
            savedPurchase.setId(1L);
            savedPurchase.setItems(new java.util.ArrayList<>());

            when(consumerDetailsService.getConsumerById(1L)).thenReturn(c);
            when(purchaseRepository.save(any())).thenReturn(savedPurchase);
            when(productService.getById(10L)).thenReturn(inactiveProduct);

            assertThatThrownBy(() -> service.createPurchase(1L, purchaseRequest(10L, 1, 0L)))
                    .isInstanceOf(ProductNotAvailableException.class);
        }

        @Test
        @DisplayName("throws InsufficientStockException when available stock is less than quantity")
        void throwsWhenInsufficientStock() {
            ConsumerDetails c = consumer(1L, "test@example.com");
            Product product = activeProduct(10L, 10_000L, 0, 30); // availableStock=0

            Purchase savedPurchase = new Purchase();
            savedPurchase.setId(1L);
            savedPurchase.setItems(new java.util.ArrayList<>());

            when(consumerDetailsService.getConsumerById(1L)).thenReturn(c);
            when(purchaseRepository.save(any())).thenReturn(savedPurchase);
            when(productService.getById(10L)).thenReturn(product);

            assertThatThrownBy(() -> service.createPurchase(1L, purchaseRequest(10L, 1, 0L)))
                    .isInstanceOf(InsufficientStockException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when keysToUse exceeds max allowed")
        void throwsWhenKeysTooHigh() {
            ConsumerDetails c = consumer(1L, "test@example.com");
            // maxKeysPct=30, price=10000 → maxKeysAllowed = 10000*30/100/1000 = 3 keys
            Product product = activeProduct(10L, 10_000L, 5, 30);

            Purchase savedPurchase = new Purchase();
            savedPurchase.setId(1L);
            savedPurchase.setReferenceId(UUID.randomUUID().toString());
            savedPurchase.setItems(new java.util.ArrayList<>());

            ProductStock stock = availableStock(1L);

            when(consumerDetailsService.getConsumerById(1L)).thenReturn(c);
            when(purchaseRepository.save(any())).thenReturn(savedPurchase);
            when(productService.getById(10L)).thenReturn(product);
            when(productStockRepository.findNextAvailableForProduct(10L)).thenReturn(Optional.of(stock));
            when(productStockRepository.save(any())).thenReturn(stock);
            when(productRepository.save(any())).thenReturn(product);

            // Requesting 100 keys when max is ~3
            assertThatThrownBy(() -> service.createPurchase(1L, purchaseRequest(10L, 1, 100L)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("keysToUse");
        }

        @Test
        @DisplayName("throws InsufficientFundsException when consumer has not enough keys in wallet")
        void throwsWhenInsufficientKeysInWallet() {
            ConsumerDetails c = consumer(1L, "test@example.com");
            // maxKeysPct=100, price=10000 → maxKeysAllowed = 10 keys
            Product product = activeProduct(10L, 10_000L, 5, 100);

            Purchase savedPurchase = new Purchase();
            savedPurchase.setId(1L);
            savedPurchase.setReferenceId(UUID.randomUUID().toString());
            savedPurchase.setItems(new java.util.ArrayList<>());

            ProductStock stock = availableStock(1L);

            KeyWallet wallet = KeyWallet.builder()
                    .id(UUID.randomUUID())
                    .purchaseKeys(2L)      // only 2 keys available
                    .blockedPurchaseKeys(0L)
                    .build();

            when(consumerDetailsService.getConsumerById(1L)).thenReturn(c);
            when(purchaseRepository.save(any())).thenReturn(savedPurchase);
            when(productService.getById(10L)).thenReturn(product);
            when(productStockRepository.findNextAvailableForProduct(10L)).thenReturn(Optional.of(stock));
            when(productStockRepository.save(any())).thenReturn(stock);
            when(productRepository.save(any())).thenReturn(product);
            when(keyWalletRepository.findByConsumerId(1L)).thenReturn(Optional.of(wallet));

            // Requesting 5 keys when wallet has only 2
            assertThatThrownBy(() -> service.createPurchase(1L, purchaseRequest(10L, 1, 5L)))
                    .isInstanceOf(InsufficientFundsException.class);
        }

        @Test
        @DisplayName("creates purchase successfully with no keys, returns checkout URL")
        void createsPurchaseSuccessfully() {
            ConsumerDetails c = consumer(1L, "buyer@example.com");
            Product product = activeProduct(10L, 10_000L, 5, 30);

            Purchase savedPurchase = new Purchase();
            savedPurchase.setId(42L);
            savedPurchase.setReferenceId("ref-abc");
            savedPurchase.setItems(new java.util.ArrayList<>());

            ProductStock stock = availableStock(1L);

            WompiCheckoutResponseDTO checkoutResponse = new WompiCheckoutResponseDTO();
            checkoutResponse.setCheckoutUrl("https://checkout.wompi.co/123");

            WompiTransaction wompiTx = WompiTransaction.builder()
                    .id(UUID.randomUUID())
                    .wompiId("wompi-123")
                    .reference("ref-abc")
                    .status(com.verygana2.models.enums.finance.WompiTransactionStatus.PENDING)
                    .type(com.verygana2.models.enums.finance.WompiTransactionType.CHARGE_COPAYMENT)
                    .amountInCents(10_000L)
                    .build();

            when(consumerDetailsService.getConsumerById(1L)).thenReturn(c);
            when(purchaseRepository.save(any())).thenReturn(savedPurchase);
            when(productService.getById(10L)).thenReturn(product);
            when(productStockRepository.findNextAvailableForProduct(10L)).thenReturn(Optional.of(stock));
            when(productStockRepository.save(any())).thenReturn(stock);
            when(productRepository.save(any())).thenReturn(product);
            when(copaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(wompiService.createCheckoutUrl(any(), any())).thenReturn(checkoutResponse);
            when(wompiTransactionRepository.findByReference(any())).thenReturn(Optional.of(wompiTx));

            InitiatePurchaseResponseDTO result = service.createPurchase(1L, purchaseRequest(10L, 1, 0L));

            assertThat(result).isNotNull();
            assertThat(result.getCheckoutUrl()).isEqualTo("https://checkout.wompi.co/123");
            assertThat(result.getStatus()).isEqualTo(PurchaseStatus.PENDING);
            verify(copaymentRepository).save(any());
        }
    }
}
