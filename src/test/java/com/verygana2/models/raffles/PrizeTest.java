package com.verygana2.models.raffles;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.models.enums.raffles.PrizeStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la entidad {@link Prize}: el contador de reclamos y la transición
 * automática a DELIVERED cuando se reclaman todas las unidades del premio.
 */
@DisplayName("Prize (entidad)")
class PrizeTest {

    @Test
    @DisplayName("incrementClaimedCount: suma 1, y si llega a la cantidad total pasa a DELIVERED")
    void incrementClaimedCount_reachesQuantity_movesToDelivered() {
        Prize prize = new Prize();
        prize.setQuantity(2);
        prize.setClaimedCount(1);
        prize.setPrizeStatus(PrizeStatus.PENDING);

        prize.incrementClaimedCount();

        assertThat(prize.getClaimedCount()).isEqualTo(2);
        assertThat(prize.getPrizeStatus()).isEqualTo(PrizeStatus.DELIVERED);
    }

    @Test
    @DisplayName("incrementClaimedCount: si aún faltan unidades por reclamar, el status no cambia")
    void incrementClaimedCount_belowQuantity_statusUnchanged() {
        Prize prize = new Prize();
        prize.setQuantity(5);
        prize.setClaimedCount(1);
        prize.setPrizeStatus(PrizeStatus.PENDING);

        prize.incrementClaimedCount();

        assertThat(prize.getClaimedCount()).isEqualTo(2);
        assertThat(prize.getPrizeStatus()).isEqualTo(PrizeStatus.PENDING);
    }

    @Test
    @DisplayName("incrementClaimedCount: si claimedCount venía null, arranca en 1")
    void incrementClaimedCount_nullClaimedCount_startsAtOne() {
        Prize prize = new Prize();
        prize.setQuantity(3);
        prize.setClaimedCount(null);

        prize.incrementClaimedCount();

        assertThat(prize.getClaimedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getImageUrl: sin imageAsset retorna null, con imageAsset arma la URL del CDN")
    void getImageUrl_dependsOnImageAsset() {
        Prize prize = new Prize();
        assertThat(prize.getImageUrl()).isNull();

        prize.setImageAsset(PrizeImageAsset.builder().objectKey("prizes/1/img.jpg").build());
        assertThat(prize.getImageUrl()).isEqualTo("https://cdn.verygana.com/public/prizes/1/img.jpg");
    }

    @Test
    @DisplayName("onCreate (hook @PrePersist): nace PENDING con claimedCount en cero")
    void onCreate_defaultsToPendingWithZeroClaims() {
        Prize prize = new Prize();

        prize.onCreate();

        assertThat(prize.getPrizeStatus()).isEqualTo(PrizeStatus.PENDING);
        assertThat(prize.getClaimedCount()).isZero();
        assertThat(prize.getCreatedAt()).isNotNull();
    }
}
