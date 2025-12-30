package com.verygana2.dtos.product.responses;

import java.time.ZonedDateTime;

import lombok.Data;

@Data
public class ProductReviewResponseDTO {
    private Long id;
    private String comment;
    private Integer rating;
    private String consumerName;
    private ZonedDateTime createdAt;
}
