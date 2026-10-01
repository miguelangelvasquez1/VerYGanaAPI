package com.verygana2.dtos.product.responses;

import java.math.BigDecimal;

import com.verygana2.dtos.targeting.TargetAudienceResponseDTO;
import com.verygana2.models.enums.marketplace.ProductType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ProductEditInfoResponseDTO {

    private Long id;

    private String name;

    private String description;

    private Long productCategoryId;

    private BigDecimal price;

    private String imageUrl;

    private Integer totalStockItems;

    private Integer availableStockItems;

    private TargetAudienceResponseDTO targeting;

    private ProductType productType;
}
