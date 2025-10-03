package com.VerYGana.dtos2.referral2.requests2;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReferralPointsRequest(

        @NotNull(message = "The referredUserId cannot be null") @Positive(message = "The referredUserId must be positive") Long referredUserId

) {

}
