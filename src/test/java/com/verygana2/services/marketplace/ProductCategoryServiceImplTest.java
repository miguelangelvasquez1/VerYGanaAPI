package com.verygana2.services.marketplace;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.product.requests.ConfirmProductCategoryCreationRequestDTO;
import com.verygana2.dtos.product.requests.CreateProductCategoryRequestDTO;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.mappers.marketplace.ProductCategoryMapper;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductCategoryImageAsset;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.repositories.ProductCategoryRepository;
import com.verygana2.repositories.marketplace.ProductCategoryImageAssetRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.services.interfaces.details.AdminDetailsService;
import com.verygana2.storage.service.AssetOrphanedService;
import com.verygana2.storage.service.R2Service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link ProductCategoryServiceImpl}: alta de categorías (mismo
 * patrón de 2 pasos que Product) y el ciclo activar/desactivar, incluyendo
 * la regla de negocio que impide borrar una categoría con productos activos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCategoryServiceImpl")
class ProductCategoryServiceImplTest {

    @Mock private ProductCategoryRepository productCategoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductCategoryImageAssetRepository productCategoryImageAssetRepository;
    @Mock private ProductCategoryMapper productCategoryMapper;
    @Mock private AdminDetailsService adminDetailsService;
    @Mock private R2Service r2Service;
    @Mock private AssetOrphanedService assetOrphanedService;

    private ProductCategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductCategoryServiceImpl(productCategoryRepository, productRepository,
                productCategoryImageAssetRepository, productCategoryMapper, adminDetailsService, r2Service,
                assetOrphanedService);
    }

    @Nested
    @DisplayName("prepareProductCategoryCreation")
    class Prepare {

        @Test
        @DisplayName("admin válido: crea el asset PENDING y retorna la URL pre-firmada")
        void validAdmin_returnsUploadPermission() {
            when(adminDetailsService.existById(1L)).thenReturn(true);
            when(productCategoryImageAssetRepository.save(any())).thenAnswer(inv -> {
                ProductCategoryImageAsset asset = inv.getArgument(0);
                asset.setId(20L);
                return asset;
            });
            when(r2Service.generateUploadUrl(eq(false), anyString(), eq("image/png")))
                    .thenReturn(new FileUploadPermissionDTO("https://upload-url", 900L));

            var result = service.prepareProductCategoryCreation(1L,
                    new FileUploadRequestDTO("img.png", "image/png", 1000L, null, null));

            assertThat(result.getAssetId()).isEqualTo(20L);
        }

        @Test
        @DisplayName("admin inexistente: lanza EntityNotFoundException")
        void unknownAdmin_throwsEntityNotFoundException() {
            when(adminDetailsService.existById(99L)).thenReturn(false);

            assertThatThrownBy(() -> service.prepareProductCategoryCreation(99L,
                    new FileUploadRequestDTO("img.png", "image/png", 1000L, null, null)))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("confirmProductCategoryCreation")
    class Confirm {

        @Test
        @DisplayName("asset ya asociado a otra categoría: lanza ValidationException y lo marca huérfano")
        void assetAlreadyLinked_throwsAndOrphansAsset() {
            ProductCategoryImageAsset asset = ProductCategoryImageAsset.builder()
                    .id(20L).status(AssetStatus.PENDING).productCategory(new ProductCategory()).build();

            when(adminDetailsService.getById(1L)).thenReturn(new AdminDetails());
            when(productCategoryImageAssetRepository.findById(20L)).thenReturn(Optional.of(asset));

            CreateProductCategoryRequestDTO data = new CreateProductCategoryRequestDTO();
            var request = ConfirmProductCategoryCreationRequestDTO.builder()
                    .productCategoryAssetId(20L).productCategoryData(data).build();

            assertThatThrownBy(() -> service.confirmProductCategoryCreation(1L, request))
                    .isInstanceOf(ValidationException.class);

            verify(assetOrphanedService).markAdAssetsAsOrphanedByIds(List.of(20L));
        }

        @Test
        @DisplayName("flujo feliz: valida en R2 y persiste la categoría vinculada al admin")
        void happyPath_persistsCategory() {
            ProductCategoryImageAsset asset = ProductCategoryImageAsset.builder()
                    .id(20L).objectKey("cat/img.jpg").sizeBytes(1000L).status(AssetStatus.PENDING).build();
            AdminDetails admin = new AdminDetails();
            ProductCategory mapped = new ProductCategory();

            when(adminDetailsService.getById(1L)).thenReturn(admin);
            when(productCategoryImageAssetRepository.findById(20L)).thenReturn(Optional.of(asset));
            when(r2Service.validateUploadedObject(eq(false), anyString(), anyLong(), anyLong(), anySet()))
                    .thenReturn(SupportedMimeType.IMAGE_PNG);
            when(productCategoryMapper.toProductCategory(any())).thenReturn(mapped);
            when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(inv -> {
                ProductCategory saved = inv.getArgument(0);
                saved.setId(3L);
                return saved;
            });

            CreateProductCategoryRequestDTO data = new CreateProductCategoryRequestDTO();
            var request = ConfirmProductCategoryCreationRequestDTO.builder()
                    .productCategoryAssetId(20L).productCategoryData(data).build();

            var response = service.confirmProductCategoryCreation(1L, request);

            assertThat(response.getId()).isEqualTo(3L);
            assertThat(mapped.getCreatedBy()).isSameAs(admin);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("categoría activa sin productos: la desactiva")
        void activeWithoutProducts_deactivatesIt() {
            ProductCategory category = new ProductCategory();
            category.setId(1L);
            category.setActive(true);

            when(productCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(productRepository.existsByProductCategoryId(1L)).thenReturn(false);

            service.delete(1L);

            assertThat(category.isActive()).isFalse();
            verify(productCategoryRepository).save(category);
        }

        @Test
        @DisplayName("categoría con productos asociados: lanza ValidationException, no la borra")
        void withAssociatedProducts_throwsValidationException() {
            ProductCategory category = new ProductCategory();
            category.setActive(true);

            when(productCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(productRepository.existsByProductCategoryId(1L)).thenReturn(true);

            assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ValidationException.class);
            verify(productCategoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("categoría ya inactiva: lanza InvalidRequestException")
        void alreadyInactive_throwsInvalidRequestException() {
            ProductCategory category = new ProductCategory();
            category.setActive(false);

            when(productCategoryRepository.findById(1L)).thenReturn(Optional.of(category));

            assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(InvalidRequestException.class);
        }
    }

    @Nested
    @DisplayName("recover")
    class Recover {

        @Test
        @DisplayName("categoría inactiva: la reactiva")
        void inactive_reactivatesIt() {
            ProductCategory category = new ProductCategory();
            category.setActive(false);

            when(productCategoryRepository.findById(1L)).thenReturn(Optional.of(category));

            service.recover(1L);

            assertThat(category.isActive()).isTrue();
        }

        @Test
        @DisplayName("categoría ya activa: lanza InvalidRequestException")
        void alreadyActive_throwsInvalidRequestException() {
            ProductCategory category = new ProductCategory();
            category.setActive(true);

            when(productCategoryRepository.findById(1L)).thenReturn(Optional.of(category));

            assertThatThrownBy(() -> service.recover(1L)).isInstanceOf(InvalidRequestException.class);
        }
    }

    @Test
    @DisplayName("getCommercialProductCategories: deduplica las categorías de todos los productos del comercial")
    void getCommercialProductCategories_deduplicatesCategories() {
        ProductCategory sameCategory = new ProductCategory();
        sameCategory.setId(1L);
        Product p1 = new Product();
        p1.setProductCategory(sameCategory);
        Product p2 = new Product();
        p2.setProductCategory(sameCategory); // misma categoría en dos productos distintos

        when(productRepository.findByCommercialId(9L)).thenReturn(List.of(p1, p2));
        when(productCategoryMapper.toProductCategoryResponseDTO(sameCategory))
                .thenReturn(new com.verygana2.dtos.product.responses.ProductCategoryResponseDTO());

        var result = service.getCommercialProductCategories(9L);

        assertThat(result).hasSize(1);
    }
}
