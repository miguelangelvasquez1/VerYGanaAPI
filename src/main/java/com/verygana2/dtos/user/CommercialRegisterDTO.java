package com.verygana2.dtos.user;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Registro básico (paso 1) de un usuario comercial. La identificación jurídica
 * (razón social, NIT, representante legal, actividad económica, etc.) se
 * completa después en el paso 3 del onboarding — ver LegalIdentificationRequestDTO.
 */
@Data
public class CommercialRegisterDTO {

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must have at least 6 characters")
    private String password;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "\\d{7,15}", message = "Phone number must have between 7 and 15 digits")
    private String phoneNumber;

}
