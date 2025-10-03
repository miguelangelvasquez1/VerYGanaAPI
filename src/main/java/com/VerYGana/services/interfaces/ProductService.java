package com.VerYGana.services.interfaces;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;

import com.VerYGana.dtos.generic.EntityCreatedResponse;
import com.VerYGana.dtos.products.Requests.CreateOrEditProductRequest;
import com.VerYGana.dtos.products.Responses.ProductSummaryResponse;

public interface ProductService {

    EntityCreatedResponse create(CreateOrEditProductRequest request, Long sellerId);

    void delete (Long productId, Long sellerId);

    void edit (Long productId, Long sellerId, CreateOrEditProductRequest createOrEditProductRequest);
    
    Page<ProductSummaryResponse> searchProducts(String searchQuery,
            Long categoryId,
            Double minRating,
            BigDecimal maxPrice,
            Integer page,
            String sortBy,
            String sortDirection);
}
