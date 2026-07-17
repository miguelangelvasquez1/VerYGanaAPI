package com.verygana2.controllers.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.product.requests.CreateProductReviewRequestDTO;
import com.verygana2.dtos.product.responses.ProductReviewResponseDTO;
import com.verygana2.services.interfaces.marketplace.ProductReviewService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link ProductReviewController}: crear reseñas, consultar
 * promedios de calificación y moderación (ocultar) por un admin.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductReviewController")
class ProductReviewControllerTest {

    @Mock private ProductReviewService productReviewService;

    private ProductReviewController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductReviewController(productReviewService);
    }

    private Jwt jwtWithUserId(Long userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(userId);
        return jwt;
    }

    @Test
    @DisplayName("getCommercialAvgRating: delega con el commercialId del JWT")
    void getCommercialAvgRating_delegates() {
        when(productReviewService.getCommercialAvgRating(9L)).thenReturn(4.2);

        assertThat(controller.getCommercialAvgRating(jwtWithUserId(9L)).getBody()).isEqualTo(4.2);
    }

    @Test
    @DisplayName("createProductReview: delega con el consumerId del JWT")
    void createProductReview_delegates() {
        CreateProductReviewRequestDTO request = new CreateProductReviewRequestDTO();
        var expected = new EntityCreatedResponseDTO(1L, "ok", java.time.Instant.now());
        when(productReviewService.createProductReview(9L, request)).thenReturn(expected);

        var response = controller.createProductReview(jwtWithUserId(9L), request);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getProductReviewsByProductId: delega con el productId y el pageable")
    void getProductReviewsByProductId_delegates() {
        var pageable = PageRequest.of(0, 20);
        var expected = PagedResponse.<ProductReviewResponseDTO>builder().build();
        when(productReviewService.getProductReviewList(1L, pageable)).thenReturn(expected);

        var response = controller.getProductReviewsByProductId(1L, pageable);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("hideProductReview: delega en el service y responde 204")
    void hideProductReview_returns204() {
        var response = controller.hideProductReview(5L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(productReviewService).hideProductReview(5L);
    }
}
