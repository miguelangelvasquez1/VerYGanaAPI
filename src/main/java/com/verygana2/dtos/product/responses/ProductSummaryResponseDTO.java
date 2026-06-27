package com.verygana2.dtos.product.responses;

import java.math.BigDecimal;

import com.verygana2.models.enums.marketplace.ProductStatus;

import lombok.Builder;
import lombok.Data;
@Data
@Builder
public class ProductSummaryResponseDTO {
    private Long id;
    private String name;
    private String imageUrl;
    private BigDecimal price;
    private Long maxKeysAllowed;
    private Long maxKeysPct;
    private Long minCashCents;
    private Double averageRate;
    private Integer reviewCount;
    private String categoryName;
    private Integer stock;
    private ProductStatus status;
    private String companyName;
    private boolean isGameReward;
}
