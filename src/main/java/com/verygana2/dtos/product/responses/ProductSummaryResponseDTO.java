package com.verygana2.dtos.product.responses;

import java.math.BigDecimal;

import lombok.Data;
@Data
public class ProductSummaryResponseDTO {
    private Long id; // product ID para el momento de agregar el producto al carrito, de la cantidad se encarga el frontend
    private String name;
    private String imageUrl;
    private BigDecimal price;
    private Double averageRate;
    private String categoryName;
    private Integer stock;
    private boolean isFavorite;
}
