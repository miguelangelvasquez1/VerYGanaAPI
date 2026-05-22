package com.verygana2.services.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.product.requests.CreateProductReviewRequestDTO;
import com.verygana2.exceptions.UnauthorizedActionException;
import com.verygana2.mappers.marketplace.ProductReviewMapper;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductReview;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.repositories.marketplace.ProductReviewRepository;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.marketplace.PurchaseItemService;
import com.verygana2.services.interfaces.marketplace.PurchaseService;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductReviewServiceImpl")
class ProductReviewServiceImplTest {

    @Mock ProductReviewRepository productReviewRepository;
    @Mock ProductReviewMapper productReviewMapper;
    @Mock ConsumerDetailsService consumerDetailsService;
    @Mock ProductRepository productRepository;
    @Mock PurchaseItemService purchaseItemService;
    @Mock PurchaseService purchaseService;

    @InjectMocks ProductReviewServiceImpl service;

    // ─── getProductAvgRating ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getProductAvgRating")
    class GetProductAvgRating {

        @Test
        @DisplayName("throws IllegalArgumentException for null product ID")
        void throwsOnNullId() {
            assertThatThrownBy(() -> service.getProductAvgRating(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for non-positive product ID")
        void throwsOnNonPositiveId() {
            assertThatThrownBy(() -> service.getProductAvgRating(0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("returns 0.0 when repository returns null (no reviews yet)")
        void returnsZeroWhenNoReviews() {
            when(productReviewRepository.productAvgRating(1L)).thenReturn(null);

            Double result = service.getProductAvgRating(1L);

            assertThat(result).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns average rating from repository")
        void returnsAvgRating() {
            when(productReviewRepository.productAvgRating(1L)).thenReturn(4.5);

            Double result = service.getProductAvgRating(1L);

            assertThat(result).isEqualTo(4.5);
        }
    }

    // ─── getCommercialAvgRating ───────────────────────────────────────────────

    @Nested
    @DisplayName("getCommercialAvgRating")
    class GetCommercialAvgRating {

        @Test
        @DisplayName("throws IllegalArgumentException for null commercial ID")
        void throwsOnNullId() {
            assertThatThrownBy(() -> service.getCommercialAvgRating(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("returns 0.0 when repository returns null")
        void returnsZeroWhenNoReviews() {
            when(productReviewRepository.commercialAvgRating(2L)).thenReturn(null);

            Double result = service.getCommercialAvgRating(2L);

            assertThat(result).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns average rating from repository")
        void returnsAvgRating() {
            when(productReviewRepository.commercialAvgRating(2L)).thenReturn(3.8);

            Double result = service.getCommercialAvgRating(2L);

            assertThat(result).isEqualTo(3.8);
        }
    }

    // ─── createProductReview ──────────────────────────────────────────────────

    @Nested
    @DisplayName("createProductReview")
    class CreateProductReview {

        private CreateProductReviewRequestDTO reviewRequest(Long purchaseItemId) {
            CreateProductReviewRequestDTO req = new CreateProductReviewRequestDTO();
            req.setPurchaseItemId(purchaseItemId);
            req.setComment("Great product!");
            req.setRating(5);
            return req;
        }

        @Test
        @DisplayName("throws UnauthorizedActionException when consumer already reviewed the product")
        void throwsWhenAlreadyReviewed() {
            Product product = new Product();
            product.setId(10L);

            PurchaseItem purchaseItem = new PurchaseItem();
            purchaseItem.setProduct(product);

            when(purchaseItemService.getByIdAndConsumerId(1L, 5L)).thenReturn(purchaseItem);
            when(productReviewRepository.existsByConsumerIdAndProductId(5L, 10L)).thenReturn(true);

            assertThatThrownBy(() -> service.createProductReview(5L, reviewRequest(1L)))
                    .isInstanceOf(UnauthorizedActionException.class)
                    .hasMessageContaining("already reviewed");
        }

        @Test
        @DisplayName("creates review, saves, updates product rating")
        void createsReviewSuccessfully() {
            Product product = new Product();
            product.setId(10L);

            PurchaseItem purchaseItem = new PurchaseItem();
            purchaseItem.setProduct(product);

            ConsumerDetails consumer = new ConsumerDetails();
            consumer.setId(5L);

            ProductReview review = new ProductReview();
            review.setId(99L);

            when(purchaseItemService.getByIdAndConsumerId(1L, 5L)).thenReturn(purchaseItem);
            when(productReviewRepository.existsByConsumerIdAndProductId(5L, 10L)).thenReturn(false);
            when(consumerDetailsService.getConsumerById(5L)).thenReturn(consumer);
            when(productReviewMapper.toProductReview(any())).thenReturn(review);
            when(productReviewRepository.save(review)).thenReturn(review);
            when(productRepository.save(product)).thenReturn(product);

            EntityCreatedResponseDTO result = service.createProductReview(5L, reviewRequest(1L));

            assertThat(result.getId()).isEqualTo(99L);
            assertThat(review.getConsumer()).isSameAs(consumer);
            assertThat(review.getProduct()).isSameAs(product);
            verify(productRepository).save(product);
        }
    }

    // ─── getPurchaseItemsToReview ─────────────────────────────────────────────

    @Nested
    @DisplayName("getPurchaseItemsToReview")
    class GetPurchaseItemsToReview {

        @Test
        @DisplayName("returns only delivered items not yet reviewed by the consumer")
        void returnsOnlyUnreviewedDeliveredItems() {
            Product productA = new Product();
            productA.setId(1L);
            productA.setName("Product A");

            Product productB = new Product();
            productB.setId(2L);
            productB.setName("Product B");

            PurchaseItem itemA = new PurchaseItem();
            itemA.setId(10L);
            itemA.setProduct(productA);
            itemA.setStatus(com.verygana2.models.enums.marketplace.PurchaseItemStatus.DELIVERED);

            PurchaseItem itemB = new PurchaseItem();
            itemB.setId(20L);
            itemB.setProduct(productB);
            itemB.setStatus(com.verygana2.models.enums.marketplace.PurchaseItemStatus.DELIVERED);

            Purchase purchase = new Purchase();
            purchase.setItems(List.of(itemA, itemB));

            when(purchaseService.getByIdAndConsumerId(100L, 5L)).thenReturn(purchase);
            // Consumer already reviewed product B
            when(productReviewRepository.findReviewedProductIdsByConsumer(5L, List.of(1L, 2L)))
                    .thenReturn(Set.of(2L));

            var result = service.getPurchaseItemsToReview(100L, 5L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("returns empty list when all delivered items are already reviewed")
        void returnsEmptyWhenAllReviewed() {
            Product product = new Product();
            product.setId(1L);
            product.setName("Prod");

            PurchaseItem item = new PurchaseItem();
            item.setId(10L);
            item.setProduct(product);
            item.setStatus(com.verygana2.models.enums.marketplace.PurchaseItemStatus.DELIVERED);

            Purchase purchase = new Purchase();
            purchase.setItems(List.of(item));

            when(purchaseService.getByIdAndConsumerId(100L, 5L)).thenReturn(purchase);
            when(productReviewRepository.findReviewedProductIdsByConsumer(5L, List.of(1L)))
                    .thenReturn(Set.of(1L));

            var result = service.getPurchaseItemsToReview(100L, 5L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when purchase has no delivered items")
        void returnsEmptyWhenNoDeliveredItems() {
            Purchase purchase = new Purchase();
            purchase.setItems(List.of());

            when(purchaseService.getByIdAndConsumerId(100L, 5L)).thenReturn(purchase);
            // findReviewedProductIdsByConsumer is only called with an empty list — it may still be called
            when(productReviewRepository.findReviewedProductIdsByConsumer(any(), any()))
                    .thenReturn(Set.of());

            var result = service.getPurchaseItemsToReview(100L, 5L);

            assertThat(result).isEmpty();
        }
    }
}
