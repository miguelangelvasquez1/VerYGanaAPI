package com.verygana2.services.interfaces.marketplace;


import java.util.List;

import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.generic.AssetUploadPermissionDTO;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.product.requests.ConfirmProductCategoryCreationRequestDTO;
import com.verygana2.dtos.product.responses.ProductCategoryResponseDTO;
import com.verygana2.models.marketplace.ProductCategory;

public interface ProductCategoryService {

    AssetUploadPermissionDTO prepareProductCategoryCreation (Long adminId, FileUploadRequestDTO productCategoryImageMetadata);
    EntityCreatedResponseDTO confirmProductCategoryCreation(Long adminId, ConfirmProductCategoryCreationRequestDTO request);
    ProductCategory getById (Long productCategoryId);
    void delete (Long productCategoryId);
    void recover (Long productCategoryId);
    List<ProductCategoryResponseDTO> getActiveProductCategories();
    List<ProductCategoryResponseDTO> getInactiveProductCategories();
    List<ProductCategoryResponseDTO> getCommercialProductCategories(Long commercialId);
}
