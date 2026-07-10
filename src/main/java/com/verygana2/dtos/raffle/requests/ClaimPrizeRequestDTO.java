package com.verygana2.dtos.raffle.requests;

import com.verygana2.models.enums.raffles.ClaimPreferenceDeliveryMethod;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ClaimPrizeRequestDTO {
    @NotNull(message = "Prize id is required")
    private Long prizeId;
    @NotNull(message = "Claim preference delivery method is required")
    private ClaimPreferenceDeliveryMethod deliveryMethod;
    @Email
    private String newEmail;
    private String emailOtpCode;
    private String newPhoneNumber;
    private String smsOtpCode;
}
