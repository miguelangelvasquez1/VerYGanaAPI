package com.verygana2.dtos.products.responses;

import java.math.BigDecimal;

public record ProductSummaryResponse (
    Long id,
    String name,
    String imagesUrl,
    BigDecimal price,
    Double averageRate,
    Integer stock
){
    
}
