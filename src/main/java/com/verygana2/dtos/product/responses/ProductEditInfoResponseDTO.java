package com.verygana2.dtos.product.responses;

import java.math.BigDecimal;

import com.verygana2.models.enums.DeliveryType;
import com.verygana2.models.enums.DigitalFormat;

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

    private DeliveryType deliveryType;

    private DigitalFormat digitalFormat;
    
    private String imageUrl;
    
    // Información de stock (sin los códigos reales)
    private Integer totalStockItems;
    private Integer availableStockItems;
}
