package com.verygana2.services.interfaces;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;

import com.verygana2.dtos.generic.EntityCreatedResponse;
import com.verygana2.dtos.products.requests.CreateOrEditProductRequest;
import com.verygana2.dtos.products.responses.ProductSummaryResponse;

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
