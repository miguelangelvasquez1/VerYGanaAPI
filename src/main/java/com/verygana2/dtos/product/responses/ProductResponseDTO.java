package com.verygana2.dtos.product.responses;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class ProductResponseDTO {
    private Long id; // product ID para el momento de agregar el producto al carrito, de la cantidad se encarga el frontend
    private String name;
    private String description;
    private String imageUrl;
    private BigDecimal price;
    private Double averageRate;
    private String categoryName;
    private String shopName;
    private Integer stock;
    private Integer reviewCount; // Cantidad de reseñas del producto
    private List<ProductReviewResponseDTO> reviews; // Lista de reseñas del producto
    private boolean isFavorite;
}
