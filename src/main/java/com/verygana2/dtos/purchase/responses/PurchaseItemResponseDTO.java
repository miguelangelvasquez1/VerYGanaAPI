package com.verygana2.dtos.purchase.responses;

import lombok.Data;

@Data
public class PurchaseItemResponseDTO {
    private Long id;
    private Long productId;
    private String productName;
    private String imageUrl;
}
