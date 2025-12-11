package com.verygana2.dtos.referral.requests;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReferralPointsRequestDTO(

        @NotNull(message = "The referredUserId cannot be null") @Positive(message = "The referredUserId must be positive") Long referredUserId

) {

}
