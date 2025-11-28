package com.verygana2.dtos.product.responses;

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
