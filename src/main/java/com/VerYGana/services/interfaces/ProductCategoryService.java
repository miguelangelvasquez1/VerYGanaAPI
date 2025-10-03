package com.VerYGana.services.interfaces;


import com.VerYGana.dtos2.products.requests2.CreateProductCategoryRequest;
import com.VerYGana.models.products.ProductCategory;

public interface ProductCategoryService {

    void create (CreateProductCategoryRequest request);
    ProductCategory getById(Long categoryId);
    void delete (Long categoryId);
    
}
