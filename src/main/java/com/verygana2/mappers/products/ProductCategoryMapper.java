package com.verygana2.mappers.products;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.product.requests.CreateProductCategoryRequest;
import com.verygana2.models.products.ProductCategory;

@Mapper(componentModel = "spring")
public interface ProductCategoryMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    ProductCategory toProductCategory (CreateProductCategoryRequest request);
    
}
