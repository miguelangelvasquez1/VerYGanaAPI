package com.verygana2.models.marketplace;

import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la entidad {@link FavoriteProduct}: solo tiene el hook de creación,
 * que sella la fecha en UTC.
 */
@DisplayName("FavoriteProduct (entidad)")
class FavoriteProductTest {

    @Test
    @DisplayName("onCreate (hook @PrePersist): sella createdAt en UTC")
    void onCreate_setsCreatedAtInUtc() {
        FavoriteProduct favorite = new FavoriteProduct();

        favorite.onCreate();

        assertThat(favorite.getCreatedAt()).isNotNull();
        assertThat(favorite.getCreatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    }
}
