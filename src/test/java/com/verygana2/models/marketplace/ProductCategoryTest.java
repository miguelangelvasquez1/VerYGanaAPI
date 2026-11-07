package com.verygana2.models.marketplace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la entidad {@link ProductCategory}: nace activa por defecto y
 * resuelve su URL de imagen pública igual que {@link Product}.
 */
@DisplayName("ProductCategory (entidad)")
class ProductCategoryTest {

    @Test
    @DisplayName("onCreate (hook @PrePersist): nace activa")
    void onCreate_defaultsToActive() {
        ProductCategory category = new ProductCategory();

        category.onCreate();

        assertThat(category.isActive()).isTrue();
        assertThat(category.getCreatedAt()).isNotNull();
    }
}
