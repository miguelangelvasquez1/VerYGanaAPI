package com.verygana2.dtos.product.requests;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfirmProductCreationRequestDTO {

    @NotNull(message = "Product asset ID is required")
    private Long productAssetId;

    @NotNull(message = "Product data is required")
    private CreateProductRequestDTO productData;
}
