package com.verygana2.models.marketplace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la entidad {@link ProductReview}: el hook de creación la marca
 * visible por defecto, y un admin puede ocultarla (moderación) sin borrarla.
 */
@DisplayName("ProductReview (entidad)")
class ProductReviewTest {

    @Test
    @DisplayName("onCreate (hook @PrePersist): la review nace visible")
    void onCreate_defaultsToVisible() {
        ProductReview review = new ProductReview();

        review.onCreate();

        assertThat(review.isVisible()).isTrue();
        assertThat(review.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("hide: oculta la review sin eliminarla (moderación de admin)")
    void hide_setsVisibleFalse() {
        ProductReview review = ProductReview.builder().visible(true).build();

        review.hide();

        assertThat(review.isVisible()).isFalse();
    }
}
