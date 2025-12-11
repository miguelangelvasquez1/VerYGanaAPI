package com.verygana2.dtos.product.requests;

import java.math.BigDecimal;
import java.util.List;

import com.verygana2.models.enums.DeliveryType;
import com.verygana2.models.enums.DigitalFormat;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateOrEditProductRequestDTO {

    @NotBlank(message = "Product name cannot be empty")
    @Size(max = 150, message = "Product name cannot exceed 150 characters")
    private String name;

    @NotBlank(message = "Product description cannot be empty")
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @NotNull(message = "ProductCategory id cannot be null")
    @Positive(message = "ProductCategory id must be a positive number")
    private Long productCategoryId;

    @NotNull(message = "Price cannot be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
    private BigDecimal price;

    private DeliveryType deliveryType;

    private DigitalFormat digitalFormat;

    // Para cargar stock digital
    @NotEmpty(message = "Product must have at least one stock item")
    private List<ProductStockRequestDTO> stockItems;
}
