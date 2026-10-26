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
    @DisplayName("isClaimed: true solo cuando el status es CLAIMED")
    void isClaimed_trueOnlyWhenDelivered() {
        assertThat(PurchaseItem.builder().status(PurchaseItemStatus.CLAIMED).build().isClaimed()).isTrue();
        assertThat(PurchaseItem.builder().status(PurchaseItemStatus.PENDING).build().isClaimed()).isFalse();
        assertThat(PurchaseItem.builder().status(PurchaseItemStatus.CANCELLED).build().isClaimed()).isFalse();
    }

    @Test
    @DisplayName("enterReview: pasa a IN_REVIEW y guarda el status anterior")
    void enterReview_movesToInReviewAndSavesPreviousStatus() {
        PurchaseItem item = PurchaseItem.builder().status(PurchaseItemStatus.CLAIMED).build();

        item.enterReview();

        assertThat(item.getStatus()).isEqualTo(PurchaseItemStatus.IN_REVIEW);
        assertThat(item.getStatusBeforeReview()).isEqualTo(PurchaseItemStatus.CLAIMED);
    }

    @Test
    @DisplayName("enterReview desde PENDING (físico aún no reclamado): guarda PENDING como previo")
    void enterReview_fromPending_savesPendingAsPrevious() {
        PurchaseItem item = PurchaseItem.builder().status(PurchaseItemStatus.PENDING).build();

        item.enterReview();

        assertThat(item.getStatus()).isEqualTo(PurchaseItemStatus.IN_REVIEW);
        assertThat(item.getStatusBeforeReview()).isEqualTo(PurchaseItemStatus.PENDING);
    }

    @Test
    @DisplayName("exitReviewDismissed: restaura exactamente el status guardado y limpia statusBeforeReview")
    void exitReviewDismissed_restoresExactPreviousStatus() {
        PurchaseItem item = PurchaseItem.builder().status(PurchaseItemStatus.PENDING).build();
        item.enterReview();

        item.exitReviewDismissed();

        assertThat(item.getStatus()).isEqualTo(PurchaseItemStatus.PENDING);
        assertThat(item.getStatusBeforeReview()).isNull();
    }

    @Test
    @DisplayName("exitReviewDismissed sin statusBeforeReview: por defecto restaura CLAIMED")
    void exitReviewDismissed_withoutPreviousStatus_defaultsToClaimed() {
        PurchaseItem item = PurchaseItem.builder().status(PurchaseItemStatus.IN_REVIEW).build();

        item.exitReviewDismissed();

        assertThat(item.getStatus()).isEqualTo(PurchaseItemStatus.CLAIMED);
    }

    // ─── Comisión e IVA ──────────────────────────────────────────────────────

    private static PurchaseItem sale(long priceCents, int commissionPct) {
        return PurchaseItem.builder()
                .unitPriceCents(priceCents)
                .subtotalCents(priceCents)
                .commissionPctApplied(commissionPct)
                .build();
    }

    @Test
    @DisplayName("BASIC 20 %: venta de $60.000 → comisión $12.000, IVA extraído $1.915,97, neto VERYGANA $10.084,03")
    void applyCommission_basic_extractsVatFromCommission() {
        PurchaseItem item = sale(6_000_000L, 20);

        item.applyCommission(6_000_000L, 19);

        assertThat(item.getCommissionCents()).isEqualTo(1_200_000L);
        assertThat(item.getCommissionVatCents()).isEqualTo(191_597L);
        assertThat(item.getCommissionNetOfVatCents()).isEqualTo(1_008_403L);
        assertThat(item.getNetToCommercialCents()).isEqualTo(4_800_000L);
    }

    @Test
    @DisplayName("STANDARD 10 %: comisión $11.900 → IVA exacto $1.900, neto $10.000")
    void applyCommission_standard_exactVat() {
        PurchaseItem item = sale(11_900_000L, 10);

        item.applyCommission(11_900_000L, 19);

        assertThat(item.getCommissionCents()).isEqualTo(1_190_000L);
        assertThat(item.getCommissionVatCents()).isEqualTo(190_000L);
        assertThat(item.getCommissionNetOfVatCents()).isEqualTo(1_000_000L);
        assertThat(item.getNetToCommercialCents()).isEqualTo(10_710_000L);
    }

    @Test
    @DisplayName("settleCommission: el IVA se extrae solo de la comisión sobre la porción no absorbida")
    void settleCommission_vatOnlyOnCommissionableBase() {
        PurchaseItem item = sale(6_000_000L, 15);

        item.settleCommission(2_000_000L, 19); // base 4.000.000 → comisión 600.000

        assertThat(item.getCommissionCents()).isEqualTo(600_000L);
        assertThat(item.getCommissionVatCents()).isEqualTo(95_798L); // 600.000 × 19 / 119 = 95.798,3
        assertThat(item.getCommissionNetOfVatCents()).isEqualTo(504_202L);
        assertThat(item.getNetToCommercialCents()).isEqualTo(5_400_000L);
    }

    @Test
    @DisplayName("IVA en cero o comisión en cero: no hay IVA")
    void applyCommission_zeroVatOrZeroCommission() {
        PurchaseItem noVat = sale(6_000_000L, 20);
        noVat.applyCommission(6_000_000L, 0);
        assertThat(noVat.getCommissionVatCents()).isZero();
        assertThat(noVat.getCommissionNetOfVatCents()).isEqualTo(1_200_000L);

        PurchaseItem noCommission = sale(6_000_000L, 0);
        noCommission.applyCommission(6_000_000L, 19);
        assertThat(noCommission.getCommissionVatCents()).isZero();
        assertThat(noCommission.getCommissionNetOfVatCents()).isZero();
    }
}
