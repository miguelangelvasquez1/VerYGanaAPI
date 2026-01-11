package com.verygana2.dtos.product.requests;

import java.math.BigDecimal;

import com.verygana2.models.enums.DeliveryType;
import com.verygana2.models.enums.DigitalFormat;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProductRequestDTO {

    @NotBlank(message = "The product name cannot be empty")
    @Size(max = 150)
    private String name;

    @NotBlank(message = "The product description cannot be empty")
    @Size(max = 500)
    private String description;

    @NotNull(message = "The product category cannot be null")
    @Positive(message = "The product category id must be positive")
    private Long productCategoryId;

    @NotNull(message = "The product price cannot be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
    private BigDecimal price;

    @NotNull(message = "The delivery type cannot be null")
    private DeliveryType deliveryType;

    @NotNull(message = "The digital format cannot be null")
    private DigitalFormat digitalFormat;
}

