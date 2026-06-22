package com.verygana2.services.interfaces.marketplace;

import java.io.IOException;
import java.math.BigDecimal;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.AssetUploadPermissionDTO;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.product.requests.ConfirmProductCreationRequestDTO;
import com.verygana2.dtos.product.requests.UpdateProductRequestDTO;

import com.verygana2.dtos.product.responses.ProductEditInfoResponseDTO;
import com.verygana2.dtos.product.responses.ProductResponseDTO;
import com.verygana2.dtos.product.responses.ProductSummaryResponseDTO;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.marketplace.Product;

public interface ProductService {

    AssetUploadPermissionDTO prepareProductCreation (Long commercialId, FileUploadRequestDTO productImageMetadata);

    EntityCreatedResponseDTO confirmProductCreation(Long commercialId, ConfirmProductCreationRequestDTO request);

    Product getById(Long productId);

    Product getByIdAndCommercialId (Long productId, Long commercialId);

    void delete (Long productId, Long commercialId);

    EntityUpdatedResponseDTO edit (Long productId, Long commercialId, UpdateProductRequestDTO request);

    AssetUploadPermissionDTO prepareProductImageUpdate(Long productId, Long commercialId,
        FileUploadRequestDTO imageMetadata);

    EntityUpdatedResponseDTO confirmProductImageUpdate(Long productId, Long commercialId,
        Long newAssetId);

    PagedResponse<ProductSummaryResponseDTO> getAllProducts(Integer page);

    PagedResponse<ProductSummaryResponseDTO> getAllProductsForAdmin (ProductStatus status, Pageable pageable);

    ProductResponseDTO approveProductForAdmin (Long adminId, Long productId);
    ProductResponseDTO rejectProductForAdmin (Long adminId, Long productId, String reason);
    void deleteProductForAdmin (Long adminId, Long productId, String reason);

    PagedResponse<ProductSummaryResponseDTO> getCommercialProducts(Long commercialId, Integer page);
    
    Long getTotalCommercialProducts (Long commercialId, ProductStatus status);

    PagedResponse<ProductSummaryResponseDTO> filterProducts(String searchQuery,
            Long categoryId,
            Double minRating,
            BigDecimal maxPrice,
            Integer page,
            String sortBy,
            String sortDirection);

    ProductResponseDTO detailProduct(Long productId);

    PagedResponse<ProductSummaryResponseDTO> getFavorites(Long consumerId, Integer page); // pending
    void addFavorite (Long consumerId, Long productId); // pending
    void removeFavorite(Long consumerId, Long productId); // pending

    ProductEditInfoResponseDTO getProductEditInfo(Long productId, Long commercialId);

    Long countFavoriteProductsByConsumerId(Long consumerId);

    void pickGameReward (Long commercialId, Long productId);

    void streamPrivateProductImage(Long productId, jakarta.servlet.http.HttpServletResponse response) throws IOException;
}
