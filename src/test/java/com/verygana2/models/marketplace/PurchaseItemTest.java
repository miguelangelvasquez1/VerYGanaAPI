package com.verygana2.models.marketplace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.models.enums.marketplace.PurchaseItemStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la entidad {@link PurchaseItem}: solo puede reseñarse un ítem
 * una vez que fue efectivamente entregado.
 */
@DisplayName("PurchaseItem (entidad)")
class PurchaseItemTest {

    @Test
    @DisplayName("isDelivered: true solo cuando el status es DELIVERED")
    void isDelivered_trueOnlyWhenDelivered() {
        assertThat(PurchaseItem.builder().status(PurchaseItemStatus.DELIVERED).build().isDelivered()).isTrue();
        assertThat(PurchaseItem.builder().status(PurchaseItemStatus.PENDING).build().isDelivered()).isFalse();
        assertThat(PurchaseItem.builder().status(PurchaseItemStatus.CANCELLED).build().isDelivered()).isFalse();
    }

    @Test
    @DisplayName("canBeReviewed: sigue exactamente la misma regla que isDelivered")
    void canBeReviewed_mirrorsIsDelivered() {
        assertThat(PurchaseItem.builder().status(PurchaseItemStatus.DELIVERED).build().canBeReviewed()).isTrue();
        assertThat(PurchaseItem.builder().status(PurchaseItemStatus.PENDING).build().canBeReviewed()).isFalse();
    }
}
