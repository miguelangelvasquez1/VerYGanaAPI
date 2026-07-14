package com.verygana2.models.marketplace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.models.enums.marketplace.PurchaseStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la entidad {@link Purchase}: agregar ítems, el snapshot financiero
 * calculado una sola vez a partir de los ítems, y la transición a completada.
 */
@DisplayName("Purchase (entidad)")
class PurchaseTest {

    private PurchaseItem item(long subtotal, long commission, long netToCommercial) {
        return PurchaseItem.builder()
                .subtotalCents(subtotal)
                .commissionCents(commission)
                .netToCommercialCents(netToCommercial)
                .build();
    }

    @Test
    @DisplayName("addItem: agrega el ítem a la lista y establece la referencia inversa (item.purchase = this)")
    void addItem_addsToListAndSetsBackReference() {
        Purchase purchase = Purchase.builder().build();
        PurchaseItem item = item(100, 10, 90);

        purchase.addItem(item);

        assertThat(purchase.getItems()).containsExactly(item);
        assertThat(item.getPurchase()).isSameAs(purchase);
    }

    @Test
    @DisplayName("calculateFinancials: suma subtotal/comisión/neto de todos los ítems")
    void calculateFinancials_sumsAllItems() {
        Purchase purchase = Purchase.builder().build();
        purchase.addItem(item(100_000, 10_000, 90_000));
        purchase.addItem(item(50_000, 5_000, 45_000));

        purchase.calculateFinancials();

        assertThat(purchase.getTotalCents()).isEqualTo(150_000);
        assertThat(purchase.getCommissionCents()).isEqualTo(15_000);
        assertThat(purchase.getNetToCommercialsCents()).isEqualTo(135_000);
    }

    @Test
    @DisplayName("calculateFinancials: sin ítems, todos los totales quedan en cero")
    void calculateFinancials_noItems_allZero() {
        Purchase purchase = Purchase.builder().build();

        purchase.calculateFinancials();

        assertThat(purchase.getTotalCents()).isZero();
        assertThat(purchase.getCommissionCents()).isZero();
        assertThat(purchase.getNetToCommercialsCents()).isZero();
    }

    @Test
    @DisplayName("markAsCompleted: pasa a COMPLETED y setea completedAt")
    void markAsCompleted_setsStatusAndTimestamp() {
        Purchase purchase = Purchase.builder().status(PurchaseStatus.PENDING).build();

        purchase.markAsCompleted();

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.COMPLETED);
        assertThat(purchase.getCompletedAt()).isNotNull();
    }
}
