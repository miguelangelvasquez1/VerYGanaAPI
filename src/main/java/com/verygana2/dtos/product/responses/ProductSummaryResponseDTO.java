package com.verygana2.dtos.product.responses;

import java.math.BigDecimal;

import lombok.Data;
@Data
public class ProductSummaryResponseDTO {
    private Long id;
    private String name;
    private String imageUrl;
    private BigDecimal price;
    private Double averageRate;
    private Integer stock;

}
