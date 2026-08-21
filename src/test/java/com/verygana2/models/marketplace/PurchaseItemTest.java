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
    @DisplayName("isDelivered: true solo cuando el status es CLAIMED")
    void isDelivered_trueOnlyWhenDelivered() {
        assertThat(PurchaseItem.builder().status(PurchaseItemStatus.CLAIMED).build().isClaimed()).isTrue();
        assertThat(PurchaseItem.builder().status(PurchaseItemStatus.PENDING).build().isClaimed()).isFalse();
        assertThat(PurchaseItem.builder().status(PurchaseItemStatus.CANCELLED).build().isClaimed()).isFalse();
    }

    @Test
    @DisplayName("canBeReviewed: sigue exactamente la misma regla que isClaimed")
    void canBeReviewed_mirrorsIsDelivered() {
        assertThat(PurchaseItem.builder().status(PurchaseItemStatus.CLAIMED).build().canBeReviewed()).isTrue();
        assertThat(PurchaseItem.builder().status(PurchaseItemStatus.PENDING).build().canBeReviewed()).isFalse();
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
}
