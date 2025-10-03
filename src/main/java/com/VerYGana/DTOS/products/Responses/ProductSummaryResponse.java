package com.VerYGana.dtos.products.responses;

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
