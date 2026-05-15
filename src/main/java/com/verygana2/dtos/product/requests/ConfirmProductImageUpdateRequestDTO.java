package com.verygana2.dtos.product.requests;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ConfirmProductImageUpdateRequestDTO {
    @NotNull
    private Long newAssetId;
}
