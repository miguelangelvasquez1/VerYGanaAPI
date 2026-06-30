package com.verygana2.dtos.product.responses;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class ProductResponseDTO {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private BigDecimal price;
    private Long maxKeysAllowed;
    private Long minCashCents;
    private Double averageRate;
    private String categoryName;
    private String companyName;
    private Integer stock;
    private Integer reviewCount;
    private List<ProductReviewResponseDTO> reviews;
    private Boolean isGameReward;
}
