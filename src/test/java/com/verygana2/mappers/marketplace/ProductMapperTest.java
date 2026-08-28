package com.verygana2.mappers.marketplace;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.dtos.product.requests.CreateProductRequestDTO;
import com.verygana2.dtos.product.requests.ProductStockRequestDTO;
import com.verygana2.dtos.product.requests.UpdateProductRequestDTO;
import com.verygana2.dtos.product.responses.ProductEditInfoResponseDTO;
import com.verygana2.dtos.product.responses.ProductResponseDTO;
import com.verygana2.dtos.product.responses.ProductSummaryResponseDTO;
import com.verygana2.mappers.TargetAudienceMapperImpl;
import com.verygana2.mappers.finance.MoneyMapper;
import com.verygana2.models.TargetAudience;
import com.verygana2.models.marketplace.FavoriteProduct;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductImageAsset;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.enums.marketplace.ProductType;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.userDetails.CommercialDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de {@link ProductMapper}: instancia la implementación real generada por MapStruct
 * ({@link ProductMapperImpl}), con {@link MoneyMapper} y {@link TargetAudienceMapperImpl}
 * reales (no mockeados, ambos son triviales) inyectados por reflexión sobre los campos
 * {@code @Autowired} que MapStruct genera (field injection, no hay constructor con args).
 */
@DisplayName("ProductMapper")
class ProductMapperTest {

    private final ProductMapperImpl mapper = newMapper();

    private static ProductMapperImpl newMapper() {
        ProductMapperImpl impl = new ProductMapperImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "moneyMapper", new MoneyMapper());
        org.springframework.test.util.ReflectionTestUtils.setField(impl, "targetAudienceMapper", new TargetAudienceMapperImpl());
        return impl;
    }

    @Nested
    @DisplayName("toProduct")
    class ToProduct {

        @Test
        @DisplayName("mapea nombre, descripción, tipo, stock y precio (convertido a centavos); el resto queda en null/default")
        void mapsManagedFields_leavesServiceManagedFieldsUnset() {
            CreateProductRequestDTO request = new CreateProductRequestDTO();
            request.setName("Producto A");
            request.setDescription("Descripción A");
            request.setProductCategoryId(1L);
            request.setPrice(BigDecimal.valueOf(15000));
            ProductStockRequestDTO stock = new ProductStockRequestDTO();
            stock.setCode("CODE-1");
            request.setStockItems(List.of(stock));
            request.setProductType(ProductType.DIGITAL);

            Product product = mapper.toProduct(request);

            assertThat(product.getName()).isEqualTo("Producto A");
            assertThat(product.getDescription()).isEqualTo("Descripción A");
            assertThat(product.getPriceCents()).isEqualTo(1_500_000L);
            assertThat(product.getProductType()).isEqualTo(ProductType.DIGITAL);
            assertThat(product.getStockItems()).hasSize(1);
            assertThat(product.getStockItems().get(0).getCode()).isEqualTo("CODE-1");

            // Campos gestionados exclusivamente por el servicio: quedan en null/default.
            assertThat(product.getId()).isNull();
            assertThat(product.getProductCategory()).isNull();
            assertThat(product.getCommercial()).isNull();
            assertThat(product.getReviews()).isEmpty();
            assertThat(product.getReviewCount()).isNull();
            assertThat(product.getAverageRate()).isNull();
            assertThat(product.getCreatedAt()).isNull();
            assertThat(product.getUpdatedAt()).isNull();
            assertThat(product.getStock()).isNull();
            // favoritedBy y reviews no se tocan (@Mapping ignore=true), pero Product los
            // inicializa con ArrayList vacío como field initializer: no quedan null.
            assertThat(product.getFavoritedBy()).isEmpty();
            assertThat(product.getImageAsset()).isNull();
            assertThat(product.getStatus()).isNull();
            assertThat(product.getApprovedBy()).isNull();
            assertThat(product.getRejectedBy()).isNull();
            assertThat(product.getDeletedBy()).isNull();
            assertThat(product.getApprovedAt()).isNull();
            assertThat(product.getRejectedAt()).isNull();
            assertThat(product.getDeletedAt()).isNull();
            assertThat(product.getRejectedUntil()).isNull();
            assertThat(product.getResubmittedAt()).isNull();
            assertThat(product.getResubmissionCount()).isNull();
            assertThat(product.getRejectionReason()).isNull();
            assertThat(product.getDeletionReason()).isNull();
            assertThat(product.getMaxKeysPct()).isNull();
            assertThat(product.getIsGameReward()).isNull();
            assertThat(product.getTargetAudience()).isNull();
            assertThat(product.getGameRewardAutoDisabled()).isNull();
        }
    }

    @Nested
    @DisplayName("toProductStock")
    class ToProductStock {

        @Test
        @DisplayName("el status queda siempre en AVAILABLE (constante del mapper)")
        void statusIsAlwaysAvailable() {
            ProductStockRequestDTO request = new ProductStockRequestDTO();
            request.setCode("CODE-XYZ");
            ZonedDateTime expiration = ZonedDateTime.now().plusDays(30);
            request.setExpirationDate(expiration);

            ProductStock stock = mapper.toProductStock(request);

            assertThat(stock.getCode()).isEqualTo("CODE-XYZ");
            assertThat(stock.getExpirationDate()).isEqualTo(expiration);
            assertThat(stock.getStatus()).isEqualTo(StockStatus.AVAILABLE);
            assertThat(stock.getId()).isNull();
            assertThat(stock.getProduct()).isNull();
        }
    }

    @Nested
    @DisplayName("updateProductFromRequest")
    class UpdateProductFromRequest {

        @Test
        @DisplayName("actualiza name/description/price; NUNCA toca productType")
        void updatesFieldsButNeverTouchesProductType() {
            Product product = new Product();
            product.setProductType(ProductType.PHYSICAL);
            product.setName("Nombre viejo");
            product.setDescription("Desc vieja");
            product.setPriceCents(1_000_000L);

            UpdateProductRequestDTO request = new UpdateProductRequestDTO();
            request.setName("Nombre nuevo");
            request.setDescription("Desc nueva");
            request.setProductCategoryId(2L);
            request.setPrice(BigDecimal.valueOf(20000));

            mapper.updateProductFromRequest(request, product);

            assertThat(product.getName()).isEqualTo("Nombre nuevo");
            assertThat(product.getDescription()).isEqualTo("Desc nueva");
            assertThat(product.getPriceCents()).isEqualTo(2_000_000L);
            // productType tiene @Mapping(ignore = true): sigue siendo PHYSICAL pese al update.
            assertThat(product.getProductType()).isEqualTo(ProductType.PHYSICAL);
        }
    }

    private Product baseProduct() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Producto");
        product.setDescription("Descripción");
        product.setPriceCents(1_500_000L);
        product.setMaxKeysPct(20);
        product.setAverageRate(4.5);
        product.setReviewCount(3);
        product.setIsGameReward(false);
        product.setProductType(ProductType.DIGITAL);
        product.setStatus(ProductStatus.ACTIVE);

        ProductCategory category = new ProductCategory();
        category.setName("Categoría A");
        product.setProductCategory(category);

        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(9L);
        commercial.setCompanyName("Empresa A");
        product.setCommercial(commercial);

        product.setReviews(List.of());
        product.setStockItems(List.of());
        return product;
    }

    @Nested
    @DisplayName("toProductResponseDTO")
    class ToProductResponseDTO {

        @Test
        @DisplayName("mapea categoryName/companyName/price y recalcula stock real vía @AfterMapping")
        void mapsRelationsAndRecalculatesStock() {
            Product product = baseProduct();

            ProductStock available = ProductStock.builder().status(StockStatus.AVAILABLE).build();
            ProductStock availableExpired = ProductStock.builder().status(StockStatus.AVAILABLE)
                    .expirationDate(ZonedDateTime.now().minusDays(1)).build();
            ProductStock sold = ProductStock.builder().status(StockStatus.SOLD).build();
            product.setStockItems(List.of(available, availableExpired, sold));

            ProductResponseDTO dto = mapper.toProductResponseDTO(product);

            assertThat(dto.getCategoryName()).isEqualTo("Categoría A");
            assertThat(dto.getCompanyName()).isEqualTo("Empresa A");
            assertThat(dto.getPrice()).isEqualByComparingTo("15000");
            // Solo el ítem AVAILABLE y no expirado cuenta como stock real.
            assertThat(dto.getStock()).isEqualTo(1);
        }

        @Test
        @DisplayName("imageUrl queda null: el mapper no lo llena en este método (hallazgo, no corregido)")
        void imageUrlIsNeverFilled() {
            Product product = baseProduct();
            ProductImageAsset asset = ProductImageAsset.builder().objectKey("products/1.png").build();
            product.setImageAsset(asset);

            ProductResponseDTO dto = mapper.toProductResponseDTO(product);

            assertThat(dto.getImageUrl()).isNull();
        }

        @Test
        @DisplayName("maxKeysPct=0: maxKeysAllowed=0 y minCashCents=priceCents completo")
        void zeroMaxKeysPct_meansNoKeysAllowedAndFullCash() {
            Product product = baseProduct();
            product.setMaxKeysPct(0);

            ProductResponseDTO dto = mapper.toProductResponseDTO(product);

            assertThat(dto.getMaxKeysAllowed()).isEqualTo(0L);
            assertThat(dto.getMinCashCents()).isEqualTo(1_500_000L);
        }

        @Test
        @DisplayName("maxKeysPct=20 (plan BASIC): calcula llaves y efectivo mínimo proporcionalmente")
        void typicalMaxKeysPct_calculatesProportionalSplit() {
            Product product = baseProduct();
            product.setMaxKeysPct(20);

            ProductResponseDTO dto = mapper.toProductResponseDTO(product);

            // maxKeysValueCents = 1_500_000 * 20 / 100 = 300_000 -> /1000 = 300 llaves
            assertThat(dto.getMaxKeysAllowed()).isEqualTo(300L);
            assertThat(dto.getMinCashCents()).isEqualTo(1_200_000L);
        }
    }

    @Nested
    @DisplayName("toProductSummaryResponseDTO(Product)")
    class ToProductSummaryResponseDTOFromProduct {

        @Test
        @DisplayName("imageUrl queda null (mismo hallazgo que toProductResponseDTO); stock vía getAvailableStock()")
        void imageUrlIsNull_stockViaAvailableStock() {
            Product product = baseProduct();
            ProductImageAsset asset = ProductImageAsset.builder().objectKey("products/1.png").build();
            product.setImageAsset(asset);
            ProductStock available = ProductStock.builder().status(StockStatus.AVAILABLE).build();
            product.setStockItems(List.of(available));

            ProductSummaryResponseDTO dto = mapper.toProductSummaryResponseDTO(product);

            assertThat(dto.getImageUrl()).isNull();
            assertThat(dto.getStock()).isEqualTo(1);
            assertThat(dto.getCategoryName()).isEqualTo("Categoría A");
            assertThat(dto.getCompanyName()).isEqualTo("Empresa A");
            assertThat(dto.getCommercialId()).isEqualTo(9L);
        }
    }

    @Nested
    @DisplayName("toProductSummaryResponseDTO(FavoriteProduct)")
    class ToProductSummaryResponseDTOFromFavoriteProduct {

        @Test
        @DisplayName("aquí SÍ se llena imageUrl, desde favoriteProduct.getProduct().getImageUrl()")
        void imageUrlIsFilledFromProduct() {
            Product product = baseProduct();
            ProductImageAsset asset = ProductImageAsset.builder().objectKey("products/1.png").build();
            product.setImageAsset(asset);
            FavoriteProduct favoriteProduct = FavoriteProduct.builder().product(product).build();

            ProductSummaryResponseDTO dto = mapper.toProductSummaryResponseDTO(favoriteProduct);

            assertThat(dto.getImageUrl()).isEqualTo("https://cdn.verygana.com/public/products/1.png");
            assertThat(dto.getName()).isEqualTo("Producto");
        }

        @Test
        @DisplayName("favoriteProduct.getProduct() == null: lanza NPE (comportamiento actual, no se corrige)")
        void nullProductInsideFavorite_throwsNpe() {
            FavoriteProduct favoriteProduct = FavoriteProduct.builder().product(null).build();

            // En producción esto no ocurre: el repositorio de favoritos solo trae
            // favoritos cuyo producto sigue activo. Se documenta el comportamiento actual.
            assertThatThrownBy(() -> mapper.toProductSummaryResponseDTO(favoriteProduct))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("toProductEditInfoDTO")
    class ToProductEditInfoDTO {

        @Test
        @DisplayName("mapea productCategoryId, price reconvertido, availableStockItems y delega targeting a TargetAudienceMapper")
        void mapsEditInfoFields() {
            Product product = baseProduct();
            product.setProductCategory(productCategoryWithId(5L, "Categoría A"));
            ProductStock available = ProductStock.builder().status(StockStatus.AVAILABLE).build();
            product.setStockItems(List.of(available));
            TargetAudience targetAudience = TargetAudience.builder().minAge(18).maxAge(40).build();
            product.setTargetAudience(targetAudience);

            ProductEditInfoResponseDTO dto = mapper.toProductEditInfoDTO(product);

            assertThat(dto.getProductCategoryId()).isEqualTo(5L);
            assertThat(dto.getAvailableStockItems()).isEqualTo(1);
            assertThat(dto.getPrice()).isEqualByComparingTo("15000");
            assertThat(dto.getTargeting()).isNotNull();
            assertThat(dto.getTargeting().getMinAge()).isEqualTo(18);
            assertThat(dto.getTargeting().getMaxAge()).isEqualTo(40);
        }

        private ProductCategory productCategoryWithId(Long id, String name) {
            ProductCategory category = new ProductCategory();
            category.setId(id);
            category.setName(name);
            return category;
        }
    }
}
