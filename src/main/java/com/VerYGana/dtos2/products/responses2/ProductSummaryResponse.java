package com.VerYGana.dtos2.products.responses2;

import java.math.BigDecimal;

public record ProductSummaryResponse (
    Long id,
    String name,
    String imageUrl,
    BigDecimal price,
    Double averageRate,
    Integer stock
){
    
}
