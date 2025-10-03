package com.VerYGana.mappers.products;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.VerYGana.dtos2.products.requests2.CreateProductCategoryRequest;
import com.VerYGana.models.products.ProductCategory;

@Mapper(componentModel = "spring")
public interface ProductCategoryMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    ProductCategory toProductCategory (CreateProductCategoryRequest request);
    
}
