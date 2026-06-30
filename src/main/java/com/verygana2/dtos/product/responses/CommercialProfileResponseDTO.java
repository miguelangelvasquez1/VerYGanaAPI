package com.verygana2.dtos.product.responses;

import java.util.List;

import com.verygana2.dtos.PagedResponse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommercialProfileResponseDTO {
    
    private String companyName;
    private String departmentName;
    private String municipalityName;
    private String registeredDate;
    private Integer reviewCount;
    private double averageRate;
    private Integer totalActiveProducts;
    private List<ProductCategoryResponseDTO> productCategories;
    private PagedResponse<ProductSummaryResponseDTO> activeProducts;
}
