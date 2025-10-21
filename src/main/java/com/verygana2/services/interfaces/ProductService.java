package com.verygana2.services.interfaces;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;

import com.verygana2.dtos.generic.EntityCreatedResponse;
import com.verygana2.dtos.products.requests.CreateOrEditProductRequest;
import com.verygana2.dtos.products.responses.ProductResponse;
import com.verygana2.dtos.products.responses.ProductSummaryResponse;
import com.verygana2.models.products.Product;

public interface ProductService {

    EntityCreatedResponse create(CreateOrEditProductRequest request, Long sellerId);

    Product getById(Long productId);

    void delete (Long productId, Long sellerId);

    // send a notification to product's owner with the reason of product elimination 
    void deleteForAdmin(Long productId);

    void edit (Long productId, Long sellerId, CreateOrEditProductRequest createOrEditProductRequest);
    
    Page<ProductSummaryResponse> searchProducts(String searchQuery,
            Long categoryId,
            Double minRating,
            BigDecimal maxPrice,
            Integer page,
            String sortBy,
            String sortDirection);

    ProductResponse detailProduct(Long productId);
}
