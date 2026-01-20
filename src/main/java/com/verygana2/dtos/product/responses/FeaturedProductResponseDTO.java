package com.verygana2.dtos.product.responses;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FeaturedProductResponseDTO {
    private Long id;
    private String name;
    private String imageUrl;
    private BigDecimal price;
    private Double averageRate;
    private Long totalSales;
}
