package com.verygana2.dtos.product.responses;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponse (
    String name,
    String description,
    List<String> imagesUrls,
    BigDecimal price,
    Integer stock,
    Double averageRate,
    String categoryName,
    String shopName,  
    String priceFormatted
){
    
}
