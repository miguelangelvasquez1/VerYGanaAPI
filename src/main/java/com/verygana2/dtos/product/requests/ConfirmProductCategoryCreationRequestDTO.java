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
public class ConfirmProductCategoryCreationRequestDTO {
    
    @NotNull(message = "Product category asset id is required")
    private Long productCategoryAssetId;

    @NotNull(message = "Product category data is required")
    private CreateProductCategoryRequestDTO productCategoryData;
}
