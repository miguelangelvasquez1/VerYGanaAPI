package com.verygana2.services.interfaces;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.product.requests.CreateProductRequestDTO;
import com.verygana2.dtos.product.requests.UpdateProductRequestDTO;
import com.verygana2.dtos.product.responses.ProductEditInfoResponseDTO;
import com.verygana2.dtos.product.responses.ProductResponseDTO;
import com.verygana2.dtos.product.responses.ProductSummaryResponseDTO;
import com.verygana2.models.products.Product;

public interface ProductService {

    EntityCreatedResponseDTO create(CreateProductRequestDTO request, Long sellerId, MultipartFile productImage);

    Product getById(Long productId);

    void delete (Long productId, Long sellerId);

    // send a notification to product's owner with the reason of product elimination 
    void deleteForAdmin(Long productId);

    EntityUpdatedResponseDTO edit (Long productId, Long sellerId, UpdateProductRequestDTO createOrEditProductRequest, MultipartFile productImage);

    PagedResponse<ProductSummaryResponseDTO> getAllProducts(Integer page);

    PagedResponse<ProductSummaryResponseDTO> getSellerProducts(Long sellerId, Integer page);
    
    Long getTotalSellerProducts (Long sellerId);

    PagedResponse<ProductSummaryResponseDTO> filterProducts(String searchQuery,
            Long categoryId,
            Double minRating,
            BigDecimal maxPrice,
            Integer page,
            String sortBy,
            String sortDirection);

    ProductResponseDTO detailProduct(Long productId);

    void getProductStats(Long productId, Long userId); // pending

    List<String> getBestSellers (); // pending

    PagedResponse<ProductSummaryResponseDTO> getFavorites(Long userId, Integer page); // pending
    void addFavorite (Long userId, Long productId); // pending
    void removeFavorite(Long userId, Long productId); // pending

    ProductEditInfoResponseDTO getProductEditInfo(Long productId, Long sellerId);
}
