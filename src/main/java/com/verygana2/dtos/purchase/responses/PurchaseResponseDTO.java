package com.verygana2.dtos.purchase.responses;

import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.models.enums.marketplace.PurchaseStatus;

import lombok.Data;

@Data
public class PurchaseResponseDTO {
    private Long id;
    private String referenceId;
    private List<PurchaseItemResponseDTO> items;
    private Integer totalItems;
    private Long totalCents;
    private Long keysValueCents;
    private Long cashCents;
    private Long commissionCents;
    private PurchaseStatus status;
    private ZonedDateTime createdAt;
    private ZonedDateTime completedAt;
}
