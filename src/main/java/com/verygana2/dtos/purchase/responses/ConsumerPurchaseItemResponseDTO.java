package com.verygana2.dtos.purchase.responses;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.marketplace.PurchaseItemStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerPurchaseItemResponseDTO {
    private Long id;
    private Long productId;
    private String productName;
    private String imageUrl;
    private Long unitPriceCents;
    private String deliveredCode;
    private ZonedDateTime deliveredAt;
    private PurchaseItemStatus status;
    private boolean canBeReviewed;
}
