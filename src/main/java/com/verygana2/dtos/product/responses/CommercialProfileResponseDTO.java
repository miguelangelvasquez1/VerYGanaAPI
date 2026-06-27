package com.verygana2.dtos.product.responses;

import java.util.List;

import com.verygana2.dtos.PagedResponse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommercialProfileResponseDTO {
    
    private String commercialName;
    private String country;
    private String city;
    private String registeredDate;
    private double averageRate;
    private Integer reviewCount;
    private Integer totalActiveProducs;
    private List<ProductCategoryResponseDTO> productCategories;
    private PagedResponse<ProductSummaryResponseDTO> products;
}
