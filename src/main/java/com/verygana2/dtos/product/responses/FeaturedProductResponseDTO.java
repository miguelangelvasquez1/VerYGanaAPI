package com.verygana2.dtos.product.responses;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FeaturedProductResponseDTO {
    private Long id;
    private String name;
    private String imageUrl;
    private BigDecimal price;
    private Double averageRate;
    private Long totalSales;
    /**
     * Promoción especial de llaves vigente para este producto, o null si rige su tope
     * ordinario. Con ella el producto admite más llaves que su tope ordinario, hasta su fecha de fin.
     */
    private ProductKeyPromotionDTO specialKeyPromotion;

    public FeaturedProductResponseDTO(Long id, String name, String imageUrl,
            Long priceCents, Double averageRate, Long totalSales) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
        this.price = BigDecimal.valueOf(priceCents).divide(BigDecimal.valueOf(100));
        this.averageRate = averageRate;
        this.totalSales = totalSales;
    }
}
