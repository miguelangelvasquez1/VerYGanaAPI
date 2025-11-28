package com.verygana2.dtos.product.responses;

import java.math.BigDecimal;

public record ProductResponse (
    String name,
    String description,
    String imageUrl,
    BigDecimal price,
    Integer stock,
    Double averageRate,
    String categoryName,
    String shopName,  
    String priceFormatted
){
    
}
