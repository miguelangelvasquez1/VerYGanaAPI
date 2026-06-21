package com.verygana2.dtos.user.gamedesigner;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordByEmailDTO {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email")
    private String email;

    @NotBlank(message = "Designer code is required")
    private String designerCode;

    @NotBlank(message = "New password is required")
    @Size(min = 6, message = "New password must have at least 6 characters")
    private String newPassword;
}
