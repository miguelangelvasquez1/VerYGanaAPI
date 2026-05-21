package com.verygana2.mappers.marketplace;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.product.requests.CreateProductCategoryRequestDTO;
import com.verygana2.dtos.product.responses.ProductCategoryResponseDTO;
import com.verygana2.models.marketplace.ProductCategory;

@Mapper(componentModel = "spring")
public interface ProductCategoryMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "imageAsset", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    ProductCategory toProductCategory (CreateProductCategoryRequestDTO request);

    @Mapping(target = "imageUrl", expression = "java(productCategory.getImageUrl())")
    ProductCategoryResponseDTO toProductCategoryResponseDTO (ProductCategory productCategory);
    
}
