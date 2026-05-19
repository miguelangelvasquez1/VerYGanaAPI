package com.verygana2.dtos.product.responses;

import java.math.BigDecimal;

import com.verygana2.models.enums.marketplace.ProductStatus;

import lombok.Data;
@Data
public class ProductSummaryResponseDTO {
    private Long id;
    private String name;
    private String imageUrl;
    private BigDecimal price;
    private Long maxKeysAllowed;
    private Long minCashCents;
    private Double averageRate;
    private String categoryName;
    private Integer stock;
    private ProductStatus status;
    private String companyName;
}
