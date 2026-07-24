package com.verygana2.dtos.product.requests;

import java.math.BigDecimal;

import com.verygana2.dtos.targeting.OptionalTargetAudienceDTO;

import jakarta.validation.Valid;
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

    /**
     * Localidades/edad/género de interés para este producto. No es una
     * restricción de acceso (nunca bloquea búsqueda ni compra): solo se usa
     * para priorizar el producto en el catálogo de consumidores afines.
     * null = sin preferencia.
     */
    @Valid
    private OptionalTargetAudienceDTO targeting;
}

