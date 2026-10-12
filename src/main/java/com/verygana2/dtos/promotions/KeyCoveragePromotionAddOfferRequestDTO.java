package com.verygana2.dtos.promotions;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record KeyCoveragePromotionAddOfferRequestDTO(@NotNull @Positive Long productId) {
}
