package com.verygana2.dtos.purchase.responses;

import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.models.enums.marketplace.PurchaseStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerPurchaseResponseDTO {
    private Long id;
    private String referenceId;
    private List<ConsumerPurchaseItemResponseDTO> items;
    private int totalItems;
    private Long totalCents;
    private Long keysValueCents;
    private Long cashCents;
    private PurchaseStatus status;
    private String deliveryEmail;
    private ZonedDateTime createdAt;
    private ZonedDateTime completedAt;
}
