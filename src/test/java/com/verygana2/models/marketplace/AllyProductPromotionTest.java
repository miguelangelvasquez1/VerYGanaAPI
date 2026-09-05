package com.verygana2.models.marketplace;

import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la entidad {@link AllyProductPromotion}: solo tiene el hook de
 * creación, que sella la fecha en UTC.
 */
@DisplayName("AllyProductPromotion (entidad)")
class AllyProductPromotionTest {

    @Test
    @DisplayName("onCreate (hook @PrePersist): sella createdAt en UTC")
    void onCreate_setsCreatedAtInUtc() {
        AllyProductPromotion promotion = new AllyProductPromotion();

        promotion.onCreate();

        assertThat(promotion.getCreatedAt()).isNotNull();
        assertThat(promotion.getCreatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    }
}
