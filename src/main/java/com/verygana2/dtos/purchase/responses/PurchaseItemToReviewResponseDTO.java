package com.verygana2.dtos.purchase.responses;

import java.time.ZonedDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PurchaseItemToReviewResponseDTO {
    private Long purchaseItemId;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private String deliveredCode;
    private ZonedDateTime deliveredAt;
}
