package com.verygana2.dtos.productReviews;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReviewableProductResponseDTO {
    private Long productId;
    private Long purchaseItemId;
    private String productName;
    private String productImageUrl;
}
