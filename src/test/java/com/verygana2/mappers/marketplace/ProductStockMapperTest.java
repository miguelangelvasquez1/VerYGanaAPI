package com.verygana2.mappers.marketplace;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.dtos.product.requests.ProductStockRequestDTO;
import com.verygana2.dtos.product.responses.ProductStockResponseDTO;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.marketplace.ProductStock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link ProductStockMapper}: instancia la implementación real generada por
 * MapStruct ({@link ProductStockMapperImpl}). El mapper no tiene dependencias inyectadas.
 *
 * <p>Ojo: este {@code toProductStock} declara {@code @Mapping(target = "status", ignore = true)},
 * a diferencia del {@code toProductStock} de {@link ProductMapper} (que usa
 * {@code constant = "AVAILABLE"}). Aquí el status AVAILABLE proviene del
 * {@code @Builder.Default} de {@link ProductStock}, no del mapper.
 */
@DisplayName("ProductStockMapper")
class ProductStockMapperTest {

    private final ProductStockMapperImpl mapper = new ProductStockMapperImpl();

    @Nested
    @DisplayName("toProductStock")
    class ToProductStock {

        @Test
        @DisplayName("mapea code y expirationDate; status queda AVAILABLE (default del builder) y el resto null")
        void mapsCodeAndExpiration() {
            ZonedDateTime expiration = ZonedDateTime.now().plusMonths(6);
            ProductStockRequestDTO request = new ProductStockRequestDTO();
            request.setCode("KEY-ABC-123");
            request.setExpirationDate(expiration);

            ProductStock stock = mapper.toProductStock(request);

            assertThat(stock.getCode()).isEqualTo("KEY-ABC-123");
            assertThat(stock.getExpirationDate()).isEqualTo(expiration);
            assertThat(stock.getStatus()).isEqualTo(StockStatus.AVAILABLE);
            assertThat(stock.getId()).isNull();
            assertThat(stock.getVersion()).isNull();
            assertThat(stock.getProduct()).isNull();
            assertThat(stock.getPurchaseItem()).isNull();
            assertThat(stock.getSoldAt()).isNull();
            assertThat(stock.getCodeHash()).isNull();
            assertThat(stock.getCreatedAt()).isNull();
            assertThat(stock.getUpdatedAt()).isNull();
        }

        @Test
        @DisplayName("expirationDate null (código que no vence): se mapea como null sin fallar")
        void nullExpirationDate_isAllowed() {
            ProductStockRequestDTO request = new ProductStockRequestDTO();
            request.setCode("KEY-NO-EXPIRA");

            ProductStock stock = mapper.toProductStock(request);

            assertThat(stock.getCode()).isEqualTo("KEY-NO-EXPIRA");
            assertThat(stock.getExpirationDate()).isNull();
        }

        @Test
        @DisplayName("request null: retorna null")
        void nullRequest_returnsNull() {
            assertThat(mapper.toProductStock(null)).isNull();
        }
    }

    @Nested
    @DisplayName("toProductStockResponseDTO")
    class ToProductStockResponseDTO {

        @Test
        @DisplayName("mapea id/status/createdAt/soldAt")
        void mapsResponseFields() {
            ZonedDateTime createdAt = ZonedDateTime.now().minusDays(10);
            ZonedDateTime soldAt = ZonedDateTime.now().minusDays(2);
            ProductStock stock = ProductStock.builder()
                    .id(42L)
                    .status(StockStatus.SOLD)
                    .createdAt(createdAt)
                    .soldAt(soldAt)
                    .build();

            ProductStockResponseDTO dto = mapper.toProductStockResponseDTO(stock);

            assertThat(dto.getId()).isEqualTo(42L);
            assertThat(dto.getStatus()).isEqualTo(StockStatus.SOLD);
            assertThat(dto.getCreatedAt()).isEqualTo(createdAt);
            assertThat(dto.getSoldAt()).isEqualTo(soldAt);
        }

        @Test
        @DisplayName("stock null: retorna null")
        void nullStock_returnsNull() {
            assertThat(mapper.toProductStockResponseDTO(null)).isNull();
        }
    }
}
