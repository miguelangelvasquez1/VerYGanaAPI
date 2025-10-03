package com.VerYGana.dtos2.products.responses2;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponse (
    Long id,
    String name,
    String description,
    List<String> imagesUrls,
    BigDecimal price,
    Integer stock,
    Double averageRate,
    String categoryName,
    String sellerName,  
    String priceFormatted,  
    String ratingStars 
){
    
}
