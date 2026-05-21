package com.verygana2.services.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.generic.AssetUploadPermissionDTO;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.product.requests.ConfirmProductCategoryCreationRequestDTO;
import com.verygana2.dtos.product.responses.ProductCategoryResponseDTO;
import com.verygana2.mappers.marketplace.ProductCategoryMapper;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductCategoryImageAsset;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.repositories.ProductCategoryRepository;
import com.verygana2.repositories.marketplace.ProductCategoryImageAssetRepository;
import com.verygana2.services.interfaces.details.AdminDetailsService;
import com.verygana2.storage.service.AssetOrphanedService;
import com.verygana2.storage.service.R2Service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCategoryServiceImpl")
class ProductCategoryServiceImplTest {

    @Mock ProductCategoryRepository productCategoryRepository;
    @Mock ProductCategoryImageAssetRepository productCategoryImageAssetRepository;
    @Mock ProductCategoryMapper productCategoryMapper;
    @Mock AdminDetailsService adminDetailsService;
    @Mock R2Service r2Service;
    @Mock AssetOrphanedService assetOrphanedService;

    @InjectMocks ProductCategoryServiceImpl service;

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private FileUploadRequestDTO validImageRequest() {
        return new FileUploadRequestDTO("category.jpg", "image/jpeg", MAX_BYTES - 1);
    }

    // ─── prepareProductCategoryCreation ──────────────────────────────────────

    @Nested
    @DisplayName("prepareProductCategoryCreation")
    class PrepareProductCategoryCreation {

        @Test
        @DisplayName("throws EntityNotFoundException when admin does not exist")
        void throwsWhenAdminNotFound() {
            when(adminDetailsService.existById(99L)).thenReturn(false);

            assertThatThrownBy(() -> service.prepareProductCategoryCreation(99L, validImageRequest()))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("throws ValidationException when file exceeds max size")
        void throwsWhenFileTooLarge() {
            when(adminDetailsService.existById(1L)).thenReturn(true);
            FileUploadRequestDTO oversized = new FileUploadRequestDTO("big.jpg", "image/jpeg", MAX_BYTES + 1);

            assertThatThrownBy(() -> service.prepareProductCategoryCreation(1L, oversized))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("throws ValidationException when MIME type is not allowed")
        void throwsWhenMimeTypeNotAllowed() {
            when(adminDetailsService.existById(1L)).thenReturn(true);
            FileUploadRequestDTO unsupported = new FileUploadRequestDTO("file.pdf", "application/pdf", 1024L);

            assertThatThrownBy(() -> service.prepareProductCategoryCreation(1L, unsupported))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("application/pdf");
        }

        @Test
        @DisplayName("saves asset and returns upload permission on valid request")
        void returnsUploadPermissionOnValidRequest() {
            when(adminDetailsService.existById(1L)).thenReturn(true);

            ProductCategoryImageAsset savedAsset = ProductCategoryImageAsset.builder()
                    .id(42L)
                    .objectKey("products-categories/key.jpg")
                    .status(AssetStatus.PENDING)
                    .build();

            FileUploadPermissionDTO permission = new FileUploadPermissionDTO("https://r2.example.com/upload", null);

            when(productCategoryImageAssetRepository.save(any())).thenReturn(savedAsset);
            when(r2Service.generateUploadUrl(eq(false), any(), any())).thenReturn(permission);

            AssetUploadPermissionDTO result = service.prepareProductCategoryCreation(1L, validImageRequest());

            assertThat(result.getAssetId()).isEqualTo(42L);
            assertThat(result.getImagePermission()).isSameAs(permission);
        }
    }

    // ─── confirmProductCategoryCreation ──────────────────────────────────────

    @Nested
    @DisplayName("confirmProductCategoryCreation")
    class ConfirmProductCategoryCreation {

        private ConfirmProductCategoryCreationRequestDTO confirmRequest(Long assetId) {
            ConfirmProductCategoryCreationRequestDTO req = new ConfirmProductCategoryCreationRequestDTO();
            req.setProductCategoryAssetId(assetId);
            return req;
        }

        @Test
        @DisplayName("throws ValidationException when asset not found")
        void throwsWhenAssetNotFound() {
            AdminDetails admin = new AdminDetails();
            when(adminDetailsService.getById(1L)).thenReturn(admin);
            when(productCategoryImageAssetRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirmProductCategoryCreation(1L, confirmRequest(99L)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("throws ValidationException when asset is already associated to a category")
        void throwsWhenAssetAlreadyAssociated() {
            AdminDetails admin = new AdminDetails();
            ProductCategory existingCategory = new ProductCategory();

            ProductCategoryImageAsset asset = ProductCategoryImageAsset.builder()
                    .id(5L)
                    .status(AssetStatus.PENDING)
                    .productCategory(existingCategory)
                    .build();

            when(adminDetailsService.getById(1L)).thenReturn(admin);
            when(productCategoryImageAssetRepository.findById(5L)).thenReturn(Optional.of(asset));

            assertThatThrownBy(() -> service.confirmProductCategoryCreation(1L, confirmRequest(5L)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("already associated");
        }

        @Test
        @DisplayName("throws ValidationException when asset status is not PENDING")
        void throwsWhenAssetNotPending() {
            AdminDetails admin = new AdminDetails();
            ProductCategoryImageAsset asset = ProductCategoryImageAsset.builder()
                    .id(5L)
                    .status(AssetStatus.VALIDATED)
                    .build();

            when(adminDetailsService.getById(1L)).thenReturn(admin);
            when(productCategoryImageAssetRepository.findById(5L)).thenReturn(Optional.of(asset));

            assertThatThrownBy(() -> service.confirmProductCategoryCreation(1L, confirmRequest(5L)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("VALIDATED");
        }

        @Test
        @DisplayName("creates category, links asset, and returns EntityCreatedResponseDTO")
        void createsSuccessfully() {
            AdminDetails admin = new AdminDetails();
            ProductCategoryImageAsset asset = ProductCategoryImageAsset.builder()
                    .id(5L)
                    .objectKey("products-categories/key.jpg")
                    .sizeBytes(1024L)
                    .status(AssetStatus.PENDING)
                    .build();

            ProductCategory category = new ProductCategory();
            category.setId(10L);

            ConfirmProductCategoryCreationRequestDTO req = new ConfirmProductCategoryCreationRequestDTO();
            req.setProductCategoryAssetId(5L);

            when(adminDetailsService.getById(1L)).thenReturn(admin);
            when(productCategoryImageAssetRepository.findById(5L)).thenReturn(Optional.of(asset));
            when(r2Service.validateUploadedObject(eq(false), any(), any(), any(), any()))
                    .thenReturn(SupportedMimeType.IMAGE_JPEG);
            when(productCategoryMapper.toProductCategory(any())).thenReturn(category);
            when(productCategoryRepository.save(category)).thenReturn(category);
            when(productCategoryImageAssetRepository.save(asset)).thenReturn(asset);

            EntityCreatedResponseDTO result = service.confirmProductCategoryCreation(1L, req);

            assertThat(result.getId()).isEqualTo(10L);
            assertThat(asset.getProductCategory()).isSameAs(category);
            assertThat(asset.getStatus()).isEqualTo(AssetStatus.VALIDATED);
            assertThat(asset.getMimeType()).isEqualTo(SupportedMimeType.IMAGE_JPEG);
        }
    }

    // ─── getById ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("returns category when found")
        void returnsCategory() {
            ProductCategory category = new ProductCategory();
            category.setId(1L);
            when(productCategoryRepository.findById(1L)).thenReturn(Optional.of(category));

            ProductCategory result = service.getById(1L);

            assertThat(result).isSameAs(category);
        }

        @Test
        @DisplayName("throws EntityNotFoundException when category not found")
        void throwsWhenNotFound() {
            when(productCategoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(99L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("soft-deletes by setting active=false and saving")
        void softDeletesCategory() {
            ProductCategory category = new ProductCategory();
            category.setId(1L);
            category.setActive(true);

            when(productCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(productCategoryRepository.save(category)).thenReturn(category);

            service.delete(1L);

            assertThat(category.isActive()).isFalse();
            verify(productCategoryRepository).save(category);
        }
    }

    // ─── getProductCategories ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getProductCategories")
    class GetProductCategories {

        @Test
        @DisplayName("returns mapped list from repository")
        void returnsMappedList() {
            ProductCategory category = new ProductCategory();
            ProductCategoryResponseDTO dto = new ProductCategoryResponseDTO();

            when(productCategoryRepository.findAvailableProductCategories()).thenReturn(List.of(category));
            when(productCategoryMapper.toProductCategoryResponseDTO(category)).thenReturn(dto);

            List<ProductCategoryResponseDTO> result = service.getProductCategories();

            assertThat(result).containsExactly(dto);
        }

        @Test
        @DisplayName("returns empty list when no categories exist")
        void returnsEmptyListWhenNone() {
            when(productCategoryRepository.findAvailableProductCategories()).thenReturn(List.of());

            List<ProductCategoryResponseDTO> result = service.getProductCategories();

            assertThat(result).isEmpty();
        }
    }
}
