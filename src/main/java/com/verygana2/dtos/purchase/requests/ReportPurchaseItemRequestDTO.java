package com.verygana2.dtos.purchase.requests;

import com.verygana2.models.enums.pqrs.MarketplaceIssueReason;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReportPurchaseItemRequestDTO {

    @NotNull(message = "Reason is required")
    private MarketplaceIssueReason reason;

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;
}
