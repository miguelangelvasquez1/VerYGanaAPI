package com.verygana2.services.interfaces;


import java.util.List;

import com.verygana2.dtos.generic.EntityCreatedResponse;
import com.verygana2.dtos.product.requests.CreateProductCategoryRequest;
import com.verygana2.dtos.product.responses.ProductCategoryResponseDTO;
import com.verygana2.models.products.ProductCategory;

public interface ProductCategoryService {

    EntityCreatedResponse create (CreateProductCategoryRequest request);
    // ProductCategoryResponseDTO getById(Long categoryId);
    ProductCategory getById (Long categoryId);
    void delete (Long categoryId);
    List<ProductCategoryResponseDTO> getProductCategories();
}
