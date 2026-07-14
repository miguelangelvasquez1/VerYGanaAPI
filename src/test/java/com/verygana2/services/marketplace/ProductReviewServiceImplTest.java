package com.verygana2.services.marketplace;

import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.product.requests.CreateProductReviewRequestDTO;
import com.verygana2.exceptions.InvalidContentException;
import com.verygana2.exceptions.UnauthorizedActionException;
import com.verygana2.mappers.marketplace.ProductReviewMapper;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductReview;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.repositories.marketplace.ProductReviewRepository;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.marketplace.PurchaseItemService;
import com.verygana2.utils.ProfanityFilterService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link ProductReviewServiceImpl}: crear una reseña valida que la
 * compra sea del consumidor, que no se repita, y filtra lenguaje ofensivo;
 * además cada alta/ocultamiento recalcula el rating agregado del producto.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductReviewServiceImpl")
class ProductReviewServiceImplTest {

    @Mock private ProductReviewRepository productReviewRepository;
    @Mock private ProductReviewMapper productReviewMapper;
    @Mock private ConsumerDetailsService consumerDetailsService;
    @Mock private ProductRepository productRepository;
    @Mock private PurchaseItemService purchaseItemService;
    @Mock private ProfanityFilterService profanityFilterService;

    private ProductReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductReviewServiceImpl(productReviewRepository, productReviewMapper, consumerDetailsService,
                productRepository, purchaseItemService, profanityFilterService);
    }

    @Nested
    @DisplayName("createProductReview")
    class CreateProductReview {

        private CreateProductReviewRequestDTO request() {
            CreateProductReviewRequestDTO dto = new CreateProductReviewRequestDTO();
            dto.setPurchaseItemId(5L);
            dto.setRating(5);
            dto.setComment("Excelente producto");
            return dto;
        }

        @Test
        @DisplayName("compra propia, sin review previa, sin lenguaje ofensivo: crea la review y recalcula el rating")
        void validReview_createsAndRecalculatesRating() {
            Product product = new Product();
            product.setId(1L);
            PurchaseItem purchaseItem = PurchaseItem.builder().product(product).build();

            when(purchaseItemService.getByIdAndConsumerId(5L, 9L)).thenReturn(purchaseItem);
            when(productReviewRepository.existsByConsumerIdAndProductId(9L, 1L)).thenReturn(false);
            when(profanityFilterService.containsProfanity("Excelente producto")).thenReturn(false);
            when(consumerDetailsService.getConsumerById(9L)).thenReturn(new ConsumerDetails());
            when(productReviewMapper.toProductReview(any())).thenReturn(new ProductReview());
            when(productReviewRepository.save(any(ProductReview.class))).thenAnswer(inv -> {
                ProductReview review = inv.getArgument(0);
                review.setId(100L);
                return review;
            });
            when(productReviewRepository.productReviewCount(1L)).thenReturn(4);
            when(productReviewRepository.productAvgRating(1L)).thenReturn(4.5);

            var response = service.createProductReview(9L, request());

            assertThat(response.getId()).isEqualTo(100L);
            // La review recalcula reviewCount/averageRate del producto contra la BD,
            // no confía en la colección en memoria.
            assertThat(product.getReviewCount()).isEqualTo(4);
            assertThat(product.getAverageRate()).isEqualTo(4.5);
            verify(productRepository).save(product);
        }

        @Test
        @DisplayName("el consumidor ya reseñó este producto: lanza UnauthorizedActionException")
        void alreadyReviewed_throwsUnauthorizedActionException() {
            Product product = new Product();
            product.setId(1L);
            PurchaseItem purchaseItem = PurchaseItem.builder().product(product).build();

            when(purchaseItemService.getByIdAndConsumerId(5L, 9L)).thenReturn(purchaseItem);
            when(productReviewRepository.existsByConsumerIdAndProductId(9L, 1L)).thenReturn(true);

            assertThatThrownBy(() -> service.createProductReview(9L, request()))
                    .isInstanceOf(UnauthorizedActionException.class);
        }

        @Test
        @DisplayName("comentario con lenguaje no permitido: lanza InvalidContentException")
        void profaneComment_throwsInvalidContentException() {
            Product product = new Product();
            product.setId(1L);
            PurchaseItem purchaseItem = PurchaseItem.builder().product(product).build();

            when(purchaseItemService.getByIdAndConsumerId(5L, 9L)).thenReturn(purchaseItem);
            when(productReviewRepository.existsByConsumerIdAndProductId(9L, 1L)).thenReturn(false);
            when(profanityFilterService.containsProfanity("Excelente producto")).thenReturn(true);

            assertThatThrownBy(() -> service.createProductReview(9L, request()))
                    .isInstanceOf(InvalidContentException.class);
        }

        @Test
        @DisplayName("purchaseItem que no pertenece al consumidor: propaga la excepción del PurchaseItemService")
        void purchaseItemNotOwned_propagatesException() {
            when(purchaseItemService.getByIdAndConsumerId(5L, 9L))
                    .thenThrow(new ObjectNotFoundException("not found", PurchaseItem.class));

            assertThatThrownBy(() -> service.createProductReview(9L, request()))
                    .isInstanceOf(ObjectNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("hideProductReview")
    class HideProductReview {

        @Test
        @DisplayName("oculta la review y recalcula el rating del producto sin ella")
        void hidesReviewAndRecalculatesRating() {
            Product product = new Product();
            product.setId(1L);
            ProductReview review = ProductReview.builder().id(100L).visible(true).product(product).build();

            when(productReviewRepository.findById(100L)).thenReturn(java.util.Optional.of(review));
            when(productReviewRepository.productReviewCount(1L)).thenReturn(3);
            when(productReviewRepository.productAvgRating(1L)).thenReturn(4.0);

            service.hideProductReview(100L);

            assertThat(review.isVisible()).isFalse();
            verify(productReviewRepository).save(review);
            verify(productRepository).save(product);
        }

        @Test
        @DisplayName("review inexistente: lanza ObjectNotFoundException")
        void notFound_throwsObjectNotFoundException() {
            when(productReviewRepository.findById(100L)).thenReturn(java.util.Optional.empty());

            assertThatThrownBy(() -> service.hideProductReview(100L)).isInstanceOf(ObjectNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getProductAvgRating / getCommercialAvgRating")
    class AvgRating {

        @Test
        @DisplayName("sin reviews aún (repositorio retorna null): el promedio por defecto es 0.0, no null")
        void noReviewsYet_defaultsToZero() {
            when(productReviewRepository.productAvgRating(1L)).thenReturn(null);
            assertThat(service.getProductAvgRating(1L)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("id de producto inválido: lanza IllegalArgumentException")
        void invalidProductId_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.getProductAvgRating(0L)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("canBeReviewed")
    class CanBeReviewed {

        @Test
        @DisplayName("sin review previa del consumidor para el producto: puede reseñar")
        void noExistingReview_canReview() {
            when(productReviewRepository.existsByConsumerIdAndProductId(9L, 1L)).thenReturn(false);
            assertThat(service.canBeReviewed(1L, 9L)).isTrue();
        }

        @Test
        @DisplayName("ya existe una review de ese consumidor para el producto: no puede reseñar de nuevo")
        void existingReview_cannotReviewAgain() {
            when(productReviewRepository.existsByConsumerIdAndProductId(9L, 1L)).thenReturn(true);
            assertThat(service.canBeReviewed(1L, 9L)).isFalse();
        }
    }
}
