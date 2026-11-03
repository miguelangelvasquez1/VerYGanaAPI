package com.verygana2.models.marketplace;

import java.math.BigDecimal;

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
    @DisplayName("settleCommission: lo absorbido paga 5 %, la base 15 %, y el IVA se extrae del total")
    void settleCommission_absorbedAndBaseAddUp() {
        PurchaseItem item = sale(6_000_000L, 15);

        // 5 % de 2.000.000 = 100.000 + 15 % de 4.000.000 = 600.000
        item.settleCommission(2_000_000L, 19, FIVE);

        assertThat(item.getCommissionCents()).isEqualTo(700_000L);
        assertThat(item.getCommissionVatCents()).isEqualTo(111_765L); // 700.000 × 19 / 119 = 111.764,7
        assertThat(item.getCommissionNetOfVatCents()).isEqualTo(588_235L);
        assertThat(item.getNetToCommercialCents()).isEqualTo(5_300_000L);
    }

    @Test
    @DisplayName("sin feature de absorción (null): lo absorbido no paga y la base paga el % del plan")
    void settleCommission_withoutAbsorbedPct_onlyBasePays() {
        PurchaseItem item = sale(6_000_000L, 15);

        item.settleCommission(2_000_000L, 19, null);

        assertThat(item.getCommissionCents()).isEqualTo(600_000L);
        assertThat(item.getProsperityMinCommissionPct()).isNull();
    }

    private static final BigDecimal FIVE = new BigDecimal("5.00");

    @Test
    @DisplayName("ejemplo del jefe: $100.000 absorbidos enteros → comisión $5.000, IVA $798,32, VERyGANA $4.201,68")
    void settleCommission_fullyAbsorbed_paysMinimum() {
        PurchaseItem item = sale(10_000_000L, 10);

        item.settleCommission(10_000_000L, 19, FIVE);

        assertThat(item.getCommissionBaseCents()).isZero();
        assertThat(item.getCommissionCents()).isEqualTo(500_000L);
        assertThat(item.getCommissionVatCents()).isEqualTo(79_832L);
        assertThat(item.getCommissionNetOfVatCents()).isEqualTo(420_168L);
        assertThat(item.getNetToCommercialCents()).isEqualTo(9_500_000L);
        assertThat(item.getProsperityMinCommissionPct()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("absorbe $90.000 de $100.000: 5 % de $90.000 ($4.500) + 10 % de $10.000 ($1.000) = $5.500")
    void settleCommission_mostlyAbsorbed_addsBothParts() {
        PurchaseItem item = sale(10_000_000L, 10);

        item.settleCommission(9_000_000L, 19, FIVE);

        assertThat(item.getCommissionCents()).isEqualTo(550_000L);
    }

    @Test
    @DisplayName("sin absorción: paga solo el % del plan")
    void settleCommission_noAbsorption_ignoresMinimum() {
        PurchaseItem item = sale(10_000_000L, 3);

        item.settleCommission(0L, 19, FIVE);

        assertThat(item.getCommissionCents()).isEqualTo(300_000L);
        assertThat(item.getProsperityMinCommissionPct()).isNull();
    }

    @Test
    @DisplayName("el % de absorción nunca supera el del plan: con plan al 3 %, lo absorbido no paga más del 3 %")
    void settleCommission_minimumCappedAtPlanPct() {
        PurchaseItem item = sale(10_000_000L, 3);

        item.settleCommission(10_000_000L, 19, FIVE);

        assertThat(item.getCommissionCents()).isEqualTo(300_000L);
        assertThat(item.getProsperityMinCommissionPct()).isEqualByComparingTo("3");
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
