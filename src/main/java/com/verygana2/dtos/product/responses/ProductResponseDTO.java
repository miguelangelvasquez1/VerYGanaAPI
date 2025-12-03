package com.verygana2.dtos.product.responses;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class ProductResponseDTO {
    private String name;
    private String description;
    private String imageUrl;
    private BigDecimal price;
    private Integer stock;
    private Double averageRate;
    private String categoryName;
    private String shopName;
    private String priceFormatted;
}
