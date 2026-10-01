package com.verygana2.mappers.marketplace;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.dtos.product.requests.CreateProductReviewRequestDTO;
import com.verygana2.dtos.product.responses.ProductReviewResponseDTO;
import com.verygana2.models.marketplace.ProductReview;
import com.verygana2.models.userDetails.ConsumerDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link ProductReviewMapper}: instancia la implementación real generada por
 * MapStruct ({@link ProductReviewMapperImpl}). El mapper no tiene dependencias inyectadas.
 */
@DisplayName("ProductReviewMapper")
class ProductReviewMapperTest {

    private final ProductReviewMapperImpl mapper = new ProductReviewMapperImpl();

    @Nested
    @DisplayName("toProductReview")
    class ToProductReview {

        @Test
        @DisplayName("mapea solo comment y rating; el resto (incluido el purchaseItem) lo resuelve el servicio")
        void mapsOnlyCommentAndRating() {
            CreateProductReviewRequestDTO request = new CreateProductReviewRequestDTO();
            request.setPurchaseItemId(99L);
            request.setComment("Excelente producto");
            request.setRating(5);

            ProductReview review = mapper.toProductReview(request);

            assertThat(review.getComment()).isEqualTo("Excelente producto");
            assertThat(review.getRating()).isEqualTo(5);
            // Campos gestionados por el servicio / hooks JPA: quedan en null/default.
            assertThat(review.getId()).isNull();
            assertThat(review.getConsumer()).isNull();
            assertThat(review.getProduct()).isNull();
            assertThat(review.getPurchaseItem()).isNull();
            assertThat(review.getCreatedAt()).isNull();
            // visible se setea en @PrePersist (onCreate), no en el mapper.
            assertThat(review.isVisible()).isFalse();
        }

        @Test
        @DisplayName("request null: retorna null")
        void nullRequest_returnsNull() {
            assertThat(mapper.toProductReview(null)).isNull();
        }
    }

    @Nested
    @DisplayName("toProductReviewResponseDTO")
    class ToProductReviewResponseDTO {

        @Test
        @DisplayName("mapea id/comment/rating/createdAt y consumerName desde consumer.name")
        void mapsFieldsAndConsumerName() {
            ConsumerDetails consumer = new ConsumerDetails();
            consumer.setName("Juana Compradora");
            ZonedDateTime createdAt = ZonedDateTime.now().minusDays(1);
            ProductReview review = ProductReview.builder()
                    .id(7L)
                    .comment("Muy bueno")
                    .rating(4)
                    .createdAt(createdAt)
                    .consumer(consumer)
                    .build();

            ProductReviewResponseDTO dto = mapper.toProductReviewResponseDTO(review);

            assertThat(dto.getId()).isEqualTo(7L);
            assertThat(dto.getComment()).isEqualTo("Muy bueno");
            assertThat(dto.getRating()).isEqualTo(4);
            assertThat(dto.getCreatedAt()).isEqualTo(createdAt);
            assertThat(dto.getConsumerName()).isEqualTo("Juana Compradora");
        }

        @Test
        @DisplayName("consumer null: consumerName queda null sin NPE")
        void nullConsumer_consumerNameIsNull() {
            ProductReview review = ProductReview.builder().id(7L).comment("x").rating(3).consumer(null).build();

            ProductReviewResponseDTO dto = mapper.toProductReviewResponseDTO(review);

            assertThat(dto.getConsumerName()).isNull();
            assertThat(dto.getRating()).isEqualTo(3);
        }

        @Test
        @DisplayName("review null: retorna null")
        void nullReview_returnsNull() {
            assertThat(mapper.toProductReviewResponseDTO(null)).isNull();
        }
    }
}
