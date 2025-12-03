package com.verygana2.dtos.product.responses;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ProductReviewResponseDTO {
    private String comment;
    private Integer rating;
    private String consumerName;
    private LocalDateTime createdAt;
}
