package com.verygana2.dtos.purchase.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ClaimPurchaseItemRequestDTO {
    @NotBlank(message = "PIN is required")
    private String pin;
}
