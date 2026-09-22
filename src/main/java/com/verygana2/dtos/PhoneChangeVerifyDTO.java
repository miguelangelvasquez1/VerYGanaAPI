package com.verygana2.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PhoneChangeVerifyDTO {

    @NotBlank(message = "El nuevo número es obligatorio")
    private String newPhoneNumber;

    @NotBlank(message = "El código OTP es obligatorio")
    private String otpCode;
}
