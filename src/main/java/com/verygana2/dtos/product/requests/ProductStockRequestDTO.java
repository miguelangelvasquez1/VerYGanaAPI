package com.verygana2.dtos.product.requests;

import java.time.ZonedDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductStockRequestDTO {
    
    @Valid
    @NotBlank(message = "Code cannot be empty")
    @Size(max = 50)
    private String code;

    @Size(max = 250)
    private String additionalInfo;

    private ZonedDateTime expirationDate;
}
