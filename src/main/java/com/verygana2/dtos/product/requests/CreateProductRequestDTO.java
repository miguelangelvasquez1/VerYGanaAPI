package com.verygana2.dtos.product.requests;

import java.math.BigDecimal;
import java.util.List;

import com.verygana2.dtos.targeting.OptionalTargetAudienceDTO;
import com.verygana2.models.enums.marketplace.ProductType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class CreateProductRequestDTO {

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

    // Para cargar stock digital
    @NotEmpty(message = "Product must have at least one stock item")
    private List<ProductStockRequestDTO> stockItems;

    /**
     * Determina si el producto se reclama automáticamente al entregar el
     * código (DIGITAL) o requiere que el comprador entregue un PIN al
     * comerciante al recoger el producto en persona (PHYSICAL). Inmutable
     * tras la creación: cambiarlo después dejaría en ambigüedad los ítems ya
     * vendidos bajo el tipo anterior.
     */
    @NotNull(message = "product type is required")
    private ProductType productType;

    /**
     * Localidades/edad/género de interés para este producto. No es una
     * restricción de acceso (nunca bloquea búsqueda ni compra): solo se usa
     * para priorizar el producto en el catálogo de consumidores afines.
     * null = sin preferencia.
     */
    @Valid
    private OptionalTargetAudienceDTO targeting;

}
