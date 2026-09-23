package com.verygana2.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PhoneChangeRequestDTO {

    @NotBlank(message = "El nuevo número es obligatorio")
    private String newPhoneNumber;
}
