package com.verygana2.dtos.product.responses;

import java.math.BigDecimal;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ProductEditInfoResponseDTO {

    private Long id;
    
    private String name;

    private String description;

    private Long productCategoryId;

    private BigDecimal price;
    
    private String imageUrl;
    
    private Integer totalStockItems;
    
    private Integer availableStockItems;
}
