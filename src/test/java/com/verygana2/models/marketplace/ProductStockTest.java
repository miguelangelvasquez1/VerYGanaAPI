package com.verygana2.models.marketplace;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.models.enums.marketplace.StockStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la entidad {@link ProductStock}: las transiciones de estado de un
 * código digital (disponible → reservado → vendido, o de vuelta a disponible
 * si la compra se cancela) y la regla de vencimiento.
 */
@DisplayName("ProductStock (entidad)")
class ProductStockTest {

    @Test
    @DisplayName("markAsReserved: pasa a RESERVED")
    void markAsReserved_setsReservedStatus() {
        ProductStock stock = ProductStock.builder().status(StockStatus.AVAILABLE).build();

        stock.markAsReserved();

        assertThat(stock.getStatus()).isEqualTo(StockStatus.RESERVED);
    }

    @Test
    @DisplayName("markAsSold: pasa a SOLD, guarda el purchaseItem y setea soldAt")
    void markAsSold_setsSoldStatusAndPurchaseItem() {
        ProductStock stock = ProductStock.builder().status(StockStatus.RESERVED).build();
        PurchaseItem item = new PurchaseItem();

        stock.markAsSold(item);

        assertThat(stock.getStatus()).isEqualTo(StockStatus.SOLD);
        assertThat(stock.getPurchaseItem()).isSameAs(item);
        assertThat(stock.getSoldAt()).isNotNull();
    }

    @Test
    @DisplayName("markAsAvailable: libera el stock (limpia purchaseItem y soldAt), usado al cancelar una compra")
    void markAsAvailable_clearsSaleInfo() {
        ProductStock stock = ProductStock.builder()
                .status(StockStatus.SOLD)
                .purchaseItem(new PurchaseItem())
                .soldAt(ZonedDateTime.now())
                .build();

        stock.markAsAvailable();

        assertThat(stock.getStatus()).isEqualTo(StockStatus.AVAILABLE);
        assertThat(stock.getPurchaseItem()).isNull();
        assertThat(stock.getSoldAt()).isNull();
    }

    @Test
    @DisplayName("isExpired: true cuando expirationDate ya pasó")
    void isExpired_trueWhenPast() {
        ProductStock stock = ProductStock.builder().expirationDate(ZonedDateTime.now().minusMinutes(1)).build();
        assertThat(stock.isExpired()).isTrue();
    }

    @Test
    @DisplayName("isExpired: false cuando no tiene fecha de expiración o aún no llega")
    void isExpired_falseWhenNoExpirationOrFuture() {
        assertThat(ProductStock.builder().expirationDate(null).build().isExpired()).isFalse();
        assertThat(ProductStock.builder().expirationDate(ZonedDateTime.now().plusDays(1)).build().isExpired()).isFalse();
    }

}
