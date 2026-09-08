package com.verygana2.mappers.marketplace;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.dtos.purchase.responses.CommercialPendingClaimResponseDTO;
import com.verygana2.dtos.purchase.responses.ConsumerPurchaseItemResponseDTO;
import com.verygana2.dtos.purchase.responses.ConsumerPurchaseResponseDTO;
import com.verygana2.dtos.purchase.responses.PurchaseItemResponseDTO;
import com.verygana2.dtos.purchase.responses.PurchaseResponseDTO;
import com.verygana2.models.enums.DocumentType;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductImageAsset;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.services.interfaces.marketplace.ProductReviewService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PurchaseMapper}: instancia la implementación real generada por MapStruct
 * ({@link PurchaseMapperImpl}) e inyecta un mock de {@link ProductReviewService} vía
 * {@link ReflectionTestUtils} en el campo protegido {@code productReviewService} (field
 * injection, la clase abstracta no tiene constructor con args). {@code productCodeEncryptor}
 * no se usa en ningún método actual, así que no hace falta stubearlo.
 */
@DisplayName("PurchaseMapper")
class PurchaseMapperTest {

    private PurchaseMapperImpl mapper;
    private ProductReviewService productReviewService;

    @BeforeEach
    void setUp() {
        mapper = new PurchaseMapperImpl();
        productReviewService = mock(ProductReviewService.class);
        ReflectionTestUtils.setField(mapper, "productReviewService", productReviewService);
    }

    private Product product(Long id, String name) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        ProductImageAsset asset = ProductImageAsset.builder().objectKey("products/" + id + ".png").build();
        product.setImageAsset(asset);
        return product;
    }

    private ConsumerDetails consumer(Long id) {
        ConsumerDetails consumer = new ConsumerDetails();
        consumer.setId(id);
        consumer.setName("Juan Comprador");
        consumer.setDocumentType(DocumentType.CC);
        consumer.setDocumentNumber("123456789");
        return consumer;
    }

    @Nested
    @DisplayName("toPurchaseItemResponseDTO")
    class ToPurchaseItemResponseDTO {

        @Test
        @DisplayName("caso normal: productId/imageUrl/productName vienen del producto")
        void normalCase_mapsFromProduct() {
            PurchaseItem item = PurchaseItem.builder()
                    .id(1L)
                    .product(product(10L, "Producto A"))
                    .build();

            PurchaseItemResponseDTO dto = mapper.toPurchaseItemResponseDTO(item);

            assertThat(dto.getProductId()).isEqualTo(10L);
            assertThat(dto.getProductName()).isEqualTo("Producto A");
            assertThat(dto.getImageUrl()).isEqualTo("https://cdn.verygana.com/public/products/10.png");
        }

        @Test
        @DisplayName("producto purgado (product=null): productName cae al snapshot, productId/imageUrl quedan null sin NPE")
        void purgedProduct_fallsBackToSnapshot() {
            PurchaseItem item = PurchaseItem.builder()
                    .id(1L)
                    .product(null)
                    .productNameSnapshot("Nombre histórico")
                    .build();

            PurchaseItemResponseDTO dto = mapper.toPurchaseItemResponseDTO(item);

            assertThat(dto.getProductName()).isEqualTo("Nombre histórico");
            assertThat(dto.getProductId()).isNull();
            assertThat(dto.getImageUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("toConsumerPurchaseItemResponseDTO")
    class ToConsumerPurchaseItemResponseDTO {

        @Test
        @DisplayName("producto purgado (product=null): canBeReviewed=false directo, SIN llamar al service")
        void purgedProduct_canBeReviewedFalseWithoutCallingService() {
            Purchase purchase = Purchase.builder().consumer(consumer(5L)).build();
            PurchaseItem item = PurchaseItem.builder()
                    .id(1L)
                    .purchase(purchase)
                    .product(null)
                    .productNameSnapshot("Nombre histórico")
                    .build();

            ConsumerPurchaseItemResponseDTO dto = mapper.toConsumerPurchaseItemResponseDTO(item);

            assertThat(dto.isCanBeReviewed()).isFalse();
            assertThat(dto.getProductName()).isEqualTo("Nombre histórico");
            verify(productReviewService, never()).canBeReviewed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("producto presente: canBeReviewed delega en productReviewService.canBeReviewed(productId, consumerId)")
        void withProduct_delegatesToProductReviewService() {
            Purchase purchase = Purchase.builder().consumer(consumer(5L)).build();
            PurchaseItem item = PurchaseItem.builder()
                    .id(1L)
                    .purchase(purchase)
                    .product(product(10L, "Producto A"))
                    .build();
            when(productReviewService.canBeReviewed(10L, 5L)).thenReturn(true);

            ConsumerPurchaseItemResponseDTO dto = mapper.toConsumerPurchaseItemResponseDTO(item);

            assertThat(dto.isCanBeReviewed()).isTrue();
            assertThat(dto.getProductId()).isEqualTo(10L);
            assertThat(dto.getImageUrl()).isEqualTo("https://cdn.verygana.com/public/products/10.png");
            verify(productReviewService).canBeReviewed(eq(10L), eq(5L));
        }
    }

    @Nested
    @DisplayName("toPurchaseResponseDTO / toConsumerPurchaseResponseDTO")
    class TotalItemsAndDelegation {

        @Test
        @DisplayName("toPurchaseResponseDTO: totalItems (Integer) = tamaño de items, mapea cada item")
        void purchaseResponseDTO_totalItemsAndItems() {
            Purchase purchase = Purchase.builder()
                    .id(1L)
                    .referenceId("REF-1")
                    .build();
            PurchaseItem item1 = PurchaseItem.builder().id(1L).product(product(10L, "A")).build();
            PurchaseItem item2 = PurchaseItem.builder().id(2L).product(product(11L, "B")).build();
            purchase.addItem(item1);
            purchase.addItem(item2);

            PurchaseResponseDTO dto = mapper.toPurchaseResponseDTO(purchase);

            assertThat(dto.getTotalItems()).isEqualTo(2);
            assertThat(dto.getItems()).hasSize(2);
            assertThat(dto.getItems().get(0).getProductId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("toConsumerPurchaseResponseDTO: totalItems (int primitivo) = tamaño de items, mapea cada item")
        void consumerPurchaseResponseDTO_totalItemsAndItems() {
            Purchase purchase = Purchase.builder()
                    .id(1L)
                    .referenceId("REF-1")
                    .consumer(consumer(5L))
                    .build();
            PurchaseItem item1 = PurchaseItem.builder().id(1L).product(product(10L, "A")).build();
            purchase.addItem(item1);
            when(productReviewService.canBeReviewed(10L, 5L)).thenReturn(false);

            ConsumerPurchaseResponseDTO dto = mapper.toConsumerPurchaseResponseDTO(purchase);

            assertThat(dto.getTotalItems()).isEqualTo(1);
            assertThat(dto.getItems()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("toCommercialPendingClaimResponseDTO")
    class ToCommercialPendingClaimResponseDTO {

        @Test
        @DisplayName("mapea productId/productName/imageUrl DIRECTO desde product (sin fallback de snapshot) y datos del comprador")
        void mapsDirectlyFromProductAndBuyer() {
            ConsumerDetails buyer = consumer(5L);
            ZonedDateTime completedAt = ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
            Purchase purchase = Purchase.builder().consumer(buyer).completedAt(completedAt).items(List.of()).build();
            PurchaseItem item = PurchaseItem.builder()
                    .id(1L)
                    .purchase(purchase)
                    .product(product(10L, "Producto A"))
                    .unitPriceCents(50_000L)
                    .build();

            CommercialPendingClaimResponseDTO dto = mapper.toCommercialPendingClaimResponseDTO(item);

            assertThat(dto.getProductId()).isEqualTo(10L);
            assertThat(dto.getProductName()).isEqualTo("Producto A");
            assertThat(dto.getImageUrl()).isEqualTo("https://cdn.verygana.com/public/products/10.png");
            assertThat(dto.getBuyerName()).isEqualTo("Juan Comprador");
            assertThat(dto.getDocumentType()).isEqualTo(DocumentType.CC);
            assertThat(dto.getDocumentNumber()).isEqualTo("123456789");
            assertThat(dto.getPurchaseAt()).isEqualTo(completedAt);
            assertThat(dto.getUnitPriceCents()).isEqualTo(50_000L);
        }

        @Test
        @DisplayName("a diferencia de los otros métodos, si product fuera null productName quedaría null (sin fallback al snapshot)")
        void nullProduct_productNameStaysNull_noSnapshotFallback() {
            Purchase purchase = Purchase.builder().consumer(consumer(5L)).items(List.of()).build();
            PurchaseItem item = PurchaseItem.builder()
                    .id(1L)
                    .purchase(purchase)
                    .product(null)
                    .productNameSnapshot("Nombre histórico") // se ignora a propósito en este método
                    .unitPriceCents(50_000L)
                    .build();

            CommercialPendingClaimResponseDTO dto = mapper.toCommercialPendingClaimResponseDTO(item);

            assertThat(dto.getProductName()).isNull();
            assertThat(dto.getProductId()).isNull();
            assertThat(dto.getImageUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("getTotalItems")
    class GetTotalItems {

        @Test
        @DisplayName("0/1/N items: retorna el tamaño exacto de la lista")
        void returnsExactListSize() {
            Purchase empty = Purchase.builder().build();
            assertThat(mapper.getTotalItems(empty)).isEqualTo(0);

            Purchase withOne = Purchase.builder().build();
            withOne.addItem(PurchaseItem.builder().id(1L).build());
            assertThat(mapper.getTotalItems(withOne)).isEqualTo(1);

            Purchase withThree = Purchase.builder().build();
            withThree.addItem(PurchaseItem.builder().id(1L).build());
            withThree.addItem(PurchaseItem.builder().id(2L).build());
            withThree.addItem(PurchaseItem.builder().id(3L).build());
            assertThat(mapper.getTotalItems(withThree)).isEqualTo(3);
        }
    }
}
