package com.verygana2.services.interfaces;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.product.requests.CreateOrEditProductRequestDTO;
import com.verygana2.dtos.product.responses.ProductResponseDTO;
import com.verygana2.dtos.product.responses.ProductSummaryResponseDTO;
import com.verygana2.models.products.Product;

public interface ProductService {

    EntityCreatedResponseDTO create(CreateOrEditProductRequestDTO request, Long sellerId, MultipartFile productImage);

    Product getById(Long productId);

    void delete (Long productId, Long sellerId);

    // send a notification to product's owner with the reason of product elimination 
    void deleteForAdmin(Long productId);

    void edit (Long productId, Long sellerId, CreateOrEditProductRequestDTO createOrEditProductRequest);

    Page<ProductSummaryResponseDTO> getAllProducts(Integer page);

    Page<ProductSummaryResponseDTO> getSellerProducts(Long sellerId, Integer page);
    
    Long getTotalSellerProducts (Long sellerId);

    Page<ProductSummaryResponseDTO> filterProducts(String searchQuery,
            Long categoryId,
            Double minRating,
            BigDecimal maxPrice,
            Integer page,
            String sortBy,
            String sortDirection);

    ProductResponseDTO detailProduct(Long productId);

    void getProductStats(Long productId, Long userId); // pending

    List<String> getBestSellers (); // pending

    Page<ProductSummaryResponseDTO> getFavorites(Long userId, Integer page); // pending
    void addFavorite (Long userId, Long productId); // pending
    void removeFavorite(Long userId, Long productId); // pending

}
