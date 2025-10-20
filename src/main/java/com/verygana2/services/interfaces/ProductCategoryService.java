package com.verygana2.services.interfaces;


import com.verygana2.dtos.generic.EntityCreatedResponse;
import com.verygana2.dtos.products.requests.CreateProductCategoryRequest;
import com.verygana2.models.products.ProductCategory;

public interface ProductCategoryService {

    EntityCreatedResponse create (CreateProductCategoryRequest request);
    ProductCategory getById(Long categoryId);
    void delete (Long categoryId);
    
}
