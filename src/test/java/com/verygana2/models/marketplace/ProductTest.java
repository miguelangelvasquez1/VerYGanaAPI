package com.verygana2.models.marketplace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.exceptions.InsufficientStockException;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.enums.marketplace.StockStatus;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de la entidad {@link Product}: cálculo de stock disponible, resolución
 * de la URL de imagen según status (pública vs. proxy privado) y el cálculo
 * de cuántas llaves puede usar un comprador para pagar el producto.
 */
@DisplayName("Product (entidad)")
class ProductTest {

    private ProductStock stock(StockStatus status, ZonedDateTime expiration) {
        return ProductStock.builder().status(status).expirationDate(expiration).build();
    }

    @Nested
    @DisplayName("getAvailableStock / getNextAvailableCode")
    class Stock {

        @Test
        @DisplayName("cuenta solo los códigos AVAILABLE y no vencidos")
        void countsOnlyAvailableAndNonExpiredCodes() {
            Product product = new Product();
            product.setName("Netflix");
            product.setStockItems(List.of(
                    stock(StockStatus.AVAILABLE, null),
                    stock(StockStatus.AVAILABLE, ZonedDateTime.now().plusDays(1)), // no vencido
                    stock(StockStatus.AVAILABLE, ZonedDateTime.now().minusDays(1)), // vencido: no cuenta
                    stock(StockStatus.SOLD, null),
                    stock(StockStatus.RESERVED, null)));

            assertThat(product.getAvailableStock()).isEqualTo(2);
        }

        @Test
        @DisplayName("getNextAvailableCode retorna el primer código disponible y no vencido")
        void nextAvailableCode_returnsFirstUsableStock() {
            ProductStock expired = stock(StockStatus.AVAILABLE, ZonedDateTime.now().minusDays(1));
            ProductStock usable = stock(StockStatus.AVAILABLE, null);
            Product product = new Product();
            product.setName("Spotify");
            product.setStockItems(List.of(expired, usable));

            assertThat(product.getNextAvailableCode()).isSameAs(usable);
        }

        @Test
        @DisplayName("sin stock disponible: lanza InsufficientStockException")
        void noAvailableStock_throwsInsufficientStockException() {
            Product product = new Product();
            product.setName("Disney+");
            product.setStockItems(List.of(stock(StockStatus.SOLD, null)));

            assertThatThrownBy(product::getNextAvailableCode)
                    .isInstanceOf(InsufficientStockException.class);
        }
    }

    @Nested
    @DisplayName("getImageUrl")
    class ImageUrl {

        @Test
        @DisplayName("sin imageAsset: retorna null")
        void withoutImageAsset_returnsNull() {
            Product product = new Product();
            assertThat(product.getImageUrl()).isNull();
        }

        @Test
        @DisplayName("con imageAsset: construye la URL pública del CDN")
        void withImageAsset_buildsCdnUrl() {
            Product product = new Product();
            ProductImageAsset asset = ProductImageAsset.builder().objectKey("products/1/img.jpg").build();
            product.setImageAsset(asset);

            assertThat(product.getImageUrl()).isEqualTo("https://cdn.verygana.com/public/products/1/img.jpg");
        }
    }

    @Nested
    @DisplayName("getMaxKeysAllowed / getMinCashCents")
    class KeysAndCash {

        @Test
        @DisplayName("40% de maxKeysPct sobre un precio de $15.000 COP = 60.000 llaves máximas")
        void computesMaxKeysAllowedFromPct() {
            Product product = new Product();
            product.setPriceCents(1_500_000L); // $15.000 COP
            product.setMaxKeysPct(40);

            // maxKeysValueCents = 1_500_000 * 40 / 100 = 600_000; / 1_000 = 600 llaves
            assertThat(product.getMaxKeysAllowed()).isEqualTo(600L);
        }

        @Test
        @DisplayName("el mínimo en efectivo es el complemento del porcentaje de llaves")
        void computesMinCashAsComplement() {
            Product product = new Product();
            product.setPriceCents(1_500_000L);
            product.setMaxKeysPct(40);

            assertThat(product.getMinCashCents()).isEqualTo(900_000L);
        }

        @Test
        @DisplayName("maxKeysPct = 0: todo el precio se paga en efectivo")
        void zeroKeysPct_allCash() {
            Product product = new Product();
            product.setPriceCents(1_000_000L);
            product.setMaxKeysPct(0);

            assertThat(product.getMaxKeysAllowed()).isZero();
            assertThat(product.getMinCashCents()).isEqualTo(1_000_000L);
        }
    }

    @Test
    @DisplayName("onCreate (hook @PrePersist): inicializa rating, contador de reviews, status PENDING y gameReward=false")
    void onCreate_setsDefaults() {
        Product product = new Product();

        product.onCreate();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.PENDING);
        assertThat(product.getAverageRate()).isEqualTo(0.0);
        assertThat(product.getReviewCount()).isZero();
        assertThat(product.getIsGameReward()).isFalse();
        assertThat(product.getCreatedAt()).isNotNull();
    }
}
