package com.verygana2.services.interfaces;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;

import com.verygana2.dtos.generic.EntityCreatedResponse;
import com.verygana2.dtos.product.requests.CreateOrEditProductRequest;
import com.verygana2.dtos.product.responses.ProductResponse;
import com.verygana2.dtos.product.responses.ProductSummaryResponse;
import com.verygana2.models.products.Product;

public interface ProductService {

    EntityCreatedResponse create(CreateOrEditProductRequest request, Long sellerId);

    Product getById(Long productId);

    void delete (Long productId, Long sellerId);

    // send a notification to product's owner with the reason of product elimination 
    void deleteForAdmin(Long productId);

    void edit (Long productId, Long sellerId, CreateOrEditProductRequest createOrEditProductRequest);

    Page<ProductSummaryResponse> getAllProducts(Integer page);

    Page<ProductSummaryResponse> getSellerProducts(Long sellerId, Integer page);
    
    Long getTotalSellerProducts (Long sellerId);

    Page<ProductSummaryResponse> filterProducts(String searchQuery,
            Long categoryId,
            Double minRating,
            BigDecimal maxPrice,
            Integer page,
            String sortBy,
            String sortDirection);

    ProductResponse detailProduct(Long productId);

    void getProductStats(Long productId, Long userId); // pending

    List<String> getBestSellers (); // pending

    Page<ProductSummaryResponse> getFavorites(Long userId, Integer page); // pending
    void addFavorite (Long userId, Long productId); // pending
    void removeFavorite(Long userId, Long productId); // pending

}
