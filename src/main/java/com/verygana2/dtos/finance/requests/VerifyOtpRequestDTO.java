package com.verygana2.dtos.finance.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyOtpRequestDTO {

    @NotBlank(message = "El código OTP es requerido")
    @Size(min = 6, max = 6, message = "El código OTP debe tener exactamente 6 dígitos")
    private String code;
}
