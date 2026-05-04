package com.verygana2.services.marketplace;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.generic.AssetUploadPermissionDTO;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.product.requests.ConfirmProductCategoryCreationRequestDTO;
import com.verygana2.dtos.product.responses.ProductCategoryResponseDTO;
import com.verygana2.mappers.marketplace.ProductCategoryMapper;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.MediaType;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductCategoryImageAsset;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.repositories.ProductCategoryRepository;
import com.verygana2.repositories.marketplace.ProductCategoryImageAssetRepository;
import com.verygana2.services.interfaces.details.AdminDetailsService;
import com.verygana2.services.interfaces.marketplace.ProductCategoryService;
import com.verygana2.storage.service.AssetOrphanedService;
import com.verygana2.storage.service.R2Service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private final ProductCategoryRepository productCategoryRepository;

    private final ProductCategoryImageAssetRepository productCategoryImageAssetRepository;

    private final ProductCategoryMapper productCategoryMapper;

    private final AdminDetailsService adminDetailsService;

    private final R2Service r2Service;

    private final AssetOrphanedService assetOrphanedService;

    private static final Set<SupportedMimeType> allowedImageMimeTypes = Set.of(SupportedMimeType.IMAGE_JPEG,
            SupportedMimeType.IMAGE_PNG,
            SupportedMimeType.IMAGE_WEBP);

    private static final int maxSizeBytesForImages = 5 * 1024 * 1024;

    @Override
    public AssetUploadPermissionDTO prepareProductCategoryCreation(Long adminId,
            FileUploadRequestDTO productCategoryImageMetadata) {

        log.info("📋 Preparing product category creation for admin: {}", adminId);

        if (!adminDetailsService.existById(adminId)) {
            throw new EntityNotFoundException("admin with id: " + adminId + " not found ");
        }

        validateFileMetadata(productCategoryImageMetadata);

        String objectKey = generateProductObjectKey(productCategoryImageMetadata);

        ProductCategoryImageAsset asset = ProductCategoryImageAsset.builder().objectKey(objectKey)
                .sizeBytes(productCategoryImageMetadata.getSizeBytes())
                .status(AssetStatus.PENDING).uploadedAt(ZonedDateTime.now(ZoneOffset.UTC)).productCategory(null)
                .build();

        ProductCategoryImageAsset savedAsset = productCategoryImageAssetRepository.save(Objects.requireNonNull(asset));

        FileUploadPermissionDTO permission = r2Service.generateUploadUrl(
                false,
                objectKey,
                productCategoryImageMetadata.getContentType());

        return AssetUploadPermissionDTO.builder().assetId(savedAsset.getId())
                .imagePermission(permission).build();
    }

    @Override
    public EntityCreatedResponseDTO confirmProductCategoryCreation(Long adminId,
            ConfirmProductCategoryCreationRequestDTO request) {

        ProductCategoryImageAsset asset = null;

        try {
            AdminDetails admin = adminDetailsService.getById(adminId);

            asset = productCategoryImageAssetRepository
                    .findById(Objects.requireNonNull(request.getProductCategoryAssetId()))
                    .orElseThrow(
                            () -> new ValidationException("Asset not found: " + request.getProductCategoryAssetId()));

            if (asset.getProductCategory() != null) {
                throw new ValidationException("Asset already associated to product category: " + asset.getId());
            }

            if (asset.getStatus() != AssetStatus.PENDING) {
                throw new ValidationException(
                        "Asset invalid status to create a product category: " + asset.getStatus());
            }

            log.info("Validating file in R2: {}", asset.getObjectKey());

            SupportedMimeType realMimeType = r2Service.validateUploadedObject(
                    false,
                    asset.getObjectKey(),
                    asset.getSizeBytes(),
                    maxSizeBytesForImages,
                    allowedImageMimeTypes);

            asset.setMimeType(realMimeType);
            asset.setStatus(AssetStatus.VALIDATED);

            ProductCategory productCategory = productCategoryMapper.toProductCategory(request.getProductCategoryData());
            productCategory.setCreatedBy(admin);
            ProductCategory savedProductCategory = productCategoryRepository
                    .save(Objects.requireNonNull(productCategory));

            asset.setProductCategory(savedProductCategory);
            asset.setStatus(AssetStatus.VALIDATED);
            asset.setMimeType(realMimeType);
            productCategoryImageAssetRepository.save(asset);

            return new EntityCreatedResponseDTO(savedProductCategory.getId(), "Product category created succesfully",
                    Instant.now());

        } catch (Exception e) {
            if (asset != null) {
                log.error("product category creation error, marking asset as orphan: {}", asset.getId());
                assetOrphanedService.markAdAssetsAsOrphanedByIds(List.of(asset.getId()));
            }
            throw e;
        }
    }

    private void validateFileMetadata(FileUploadRequestDTO metadata) {

        if (metadata.getSizeBytes() > maxSizeBytesForImages) {
            throw new ValidationException(
                    String.format("Archivo muy grande. Máximo permitido para %s: %d MB",
                            MediaType.IMAGE, maxSizeBytesForImages));
        }

        // Validar contra el Set de tipos permitidos
        boolean isAllowed = allowedImageMimeTypes.stream()
                .anyMatch(mime -> mime.getMime().equals(metadata.getContentType()));

        if (!isAllowed) {
            throw new ValidationException("Tipo de archivo no permitido: " + metadata.getContentType());
        }

    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot);
    }

    private String generateProductObjectKey(FileUploadRequestDTO metadata) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(metadata.getOriginalFileName());

        return String.format("products-categories/%s-%s%s",
                timestamp, uuid, extension);
    }

    @Override
    public void delete(Long categoryId) {
        ProductCategory category = getById(categoryId);
        category.setActive(false);
        productCategoryRepository.save(category);
    }

    @Override
    public ProductCategory getById(Long categoryId) {
        return productCategoryRepository.findById(Objects.requireNonNull(categoryId))
                .orElseThrow(() -> new EntityNotFoundException("ProductCategory with id: " + categoryId + " not found"));

    }

    @Override
    public List<ProductCategoryResponseDTO> getProductCategories() {
        return productCategoryRepository.findAvailableProductCategories().stream()
                .map(productCategoryMapper::toProductCategoryResponseDTO).toList();
    }

}
