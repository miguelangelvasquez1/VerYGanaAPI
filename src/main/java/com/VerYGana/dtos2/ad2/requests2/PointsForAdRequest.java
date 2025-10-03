package com.VerYGana.dtos2.ad2.requests2;



import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PointsForAdRequest(

        @NotNull(message = "The adId cannot be null") @Positive(message = "The adId must be positive") Long adId) {
}
