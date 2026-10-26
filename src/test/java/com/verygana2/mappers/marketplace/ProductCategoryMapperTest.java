package com.verygana2.mappers.marketplace;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.dtos.product.requests.CreateProductCategoryRequestDTO;
import com.verygana2.dtos.product.responses.ProductCategoryResponseDTO;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductCategoryImageAsset;

import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.testsupport.PublicUrlBuilders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link ProductCategoryMapper}: instancia la implementación real generada por
 * MapStruct ({@link ProductCategoryMapperImpl}). El mapper no tiene dependencias inyectadas.
 */
@DisplayName("ProductCategoryMapper")
class ProductCategoryMapperTest {

    private final ProductCategoryMapperImpl mapper = newMapper();

    private static ProductCategoryMapperImpl newMapper() {
        ProductCategoryMapperImpl impl = new ProductCategoryMapperImpl();
        ReflectionTestUtils.setField(impl, "publicUrlBuilder", PublicUrlBuilders.forDomain("cdn-beta.verygana.com"));
        return impl;
    }

    @Nested
    @DisplayName("toProductCategory")
    class ToProductCategory {

        @Test
        @DisplayName("mapea solo el nombre; active/id/createdAt/imageAsset/createdBy quedan en default/null")
        void mapsOnlyName() {
            CreateProductCategoryRequestDTO request = new CreateProductCategoryRequestDTO();
            request.setName("Streaming");

            ProductCategory category = mapper.toProductCategory(request);

            assertThat(category.getName()).isEqualTo("Streaming");
            assertThat(category.getId()).isNull();
            assertThat(category.getCreatedAt()).isNull();
            assertThat(category.getImageAsset()).isNull();
            assertThat(category.getCreatedBy()).isNull();
            // active se setea en @PrePersist (onCreate), no en el mapper -> default primitivo false.
            assertThat(category.isActive()).isFalse();
        }

        @Test
        @DisplayName("request null: retorna null")
        void nullRequest_returnsNull() {
            assertThat(mapper.toProductCategory(null)).isNull();
        }
    }

    @Nested
    @DisplayName("toProductCategoryResponseDTO")
    class ToProductCategoryResponseDTO {

        @Test
        @DisplayName("sin imageAsset: mapea id/name y imageUrl queda null")
        void imageUrlNullWithoutAsset() {
            ProductCategory category = new ProductCategory();
            category.setId(3L);
            category.setName("Videojuegos");
            category.setCreatedAt(ZonedDateTime.now());

            ProductCategoryResponseDTO dto = mapper.toProductCategoryResponseDTO(category);

            assertThat(dto.getId()).isEqualTo(3L);
            assertThat(dto.getName()).isEqualTo("Videojuegos");
            assertThat(dto.getImageUrl()).isNull();
        }

        @Test
        @DisplayName("con imageAsset: imageUrl se arma con la URL pública del CDN (dominio configurado)")
        void imageUrlUsesConfiguredCdn() {
            ProductCategory category = new ProductCategory();
            category.setId(3L);
            category.setName("Videojuegos");
            category.setImageAsset(ProductCategoryImageAsset.builder().objectKey("categories/3/img.jpg").build());

            ProductCategoryResponseDTO dto = mapper.toProductCategoryResponseDTO(category);

            assertThat(dto.getImageUrl()).isEqualTo("https://cdn-beta.verygana.com/public/categories/3/img.jpg");
        }

        @Test
        @DisplayName("category null: retorna null")
        void nullCategory_returnsNull() {
            assertThat(mapper.toProductCategoryResponseDTO(null)).isNull();
        }
    }
}
