package com.verygana2.dtos.user.admin;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EditBasicInfoRequestDTO {
    @NotBlank(message = "Email is required")
    private String email;
    @NotBlank(message = "Phone number is required")
    private String phoneNumber;
}
