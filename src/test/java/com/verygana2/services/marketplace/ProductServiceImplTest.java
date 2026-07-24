package com.verygana2.services.marketplace;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.generic.AssetUploadPermissionDTO;
import com.verygana2.dtos.product.requests.ConfirmProductCreationRequestDTO;
import com.verygana2.dtos.product.requests.CreateProductRequestDTO;
import com.verygana2.dtos.product.responses.ProductResponseDTO;
import com.verygana2.exceptions.FavoriteProductException;
import com.verygana2.exceptions.GameRewardException;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.mappers.marketplace.ProductMapper;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.marketplace.FavoriteProduct;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductImageAsset;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.marketplace.FavoriteProductRepository;
import com.verygana2.repositories.marketplace.ProductImageAssetRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.repositories.marketplace.ProductStockRepository;
import com.verygana2.security.ProductCodeEncryptor;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.details.AdminDetailsService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.marketplace.ProductCategoryService;
import com.verygana2.storage.service.AssetOrphanedService;
import com.verygana2.storage.service.R2Service;
import com.verygana2.utils.validators.TargetAudienceAssembler;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link ProductServiceImpl}: el flujo más largo de Marketplace
 * (subida de imagen en 2 pasos, validación de precio, moderación por admin,
 * favoritos y el límite de 3 productos marcados como premio de juego).
 * maxProductPriceCents/minProductPriceCents/appBaseUrl se inyectan vía
 * ReflectionTestUtils porque son campos @Value que Spring solo llena en
 * tiempo de ejecución real, no al construir la clase a mano en el test.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceImpl")
class ProductServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private AdminDetailsService adminDetailsService;
    @Mock private NotificationService notificationService;
    @Mock private FavoriteProductRepository favoriteProductRepository;
    @Mock private ProductCategoryService productCategoryService;
    @Mock private ProductMapper productMapper;
    @Mock private CommercialDetailsRepository commercialDetailsRepository;
    @Mock private ConsumerDetailsService consumerDetailsService;
    @Mock private ProductStockRepository productStockRepository;
    @Mock private ProductImageAssetRepository productImageAssetRepository;
    @Mock private R2Service r2Service;
    @Mock private AssetOrphanedService assetOrphanedService;
    @Mock private ProductCodeEncryptor productCodeEncryptor;
    @Mock private TargetAudienceAssembler targetAudienceAssembler;

    private ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductServiceImpl(productRepository, adminDetailsService, notificationService,
                favoriteProductRepository, productCategoryService, productMapper, commercialDetailsRepository,
                consumerDetailsService, productStockRepository, productImageAssetRepository, r2Service,
                assetOrphanedService, productCodeEncryptor, targetAudienceAssembler);

        ReflectionTestUtils.setField(service, "maxProductPriceCents", 50_000_000L);
        ReflectionTestUtils.setField(service, "minProductPriceCents", 100_000L);
        ReflectionTestUtils.setField(service, "appBaseUrl", "https://api.verygana.com");
    }

    private Product product(Long id, ProductStatus status) {
        Product product = new Product();
        product.setId(id);
        product.setName("Netflix Premium");
        product.setStatus(status);
        product.setPriceCents(1_000_000L);
        product.setMaxKeysPct(30);
        return product;
    }

    private CommercialDetails commercial(Long userId) {
        CommercialDetails commercial = new CommercialDetails();
        com.verygana2.models.User user = new com.verygana2.models.User();
        user.setId(userId);
        commercial.setUser(user);
        Plan plan = Plan.builder().maxKeysPct(30).build();
        commercial.setCurrentPlan(plan);
        return commercial;
    }

    // ─── Creación de producto (2 pasos: prepare + confirm) ─────────────────────

    @Nested
    @DisplayName("prepareProductCreation")
    class PrepareProductCreation {

        @Test
        @DisplayName("commercial válido y archivo válido: crea el asset PENDING y retorna la URL pre-firmada")
        void validCommercial_returnsUploadPermission() {
            FileUploadRequestDTO metadata = new FileUploadRequestDTO("img.jpg", "image/png", 1000L, null, null);
            when(commercialDetailsRepository.existsByUser_Id(1L)).thenReturn(true);
            when(productImageAssetRepository.save(any())).thenAnswer(inv -> {
                ProductImageAsset asset = inv.getArgument(0);
                asset.setId(500L);
                return asset;
            });
            when(r2Service.generateUploadUrl(eq(true), anyString(), eq("image/png")))
                    .thenReturn(new FileUploadPermissionDTO("https://upload-url", 900L));

            AssetUploadPermissionDTO result = service.prepareProductCreation(1L, metadata);

            assertThat(result.getAssetId()).isEqualTo(500L);
            assertThat(result.getImagePermission().getUploadUrl()).isEqualTo("https://upload-url");
        }

        @Test
        @DisplayName("commercial inexistente: lanza EntityNotFoundException")
        void unknownCommercial_throwsEntityNotFoundException() {
            when(commercialDetailsRepository.existsByUser_Id(99L)).thenReturn(false);

            assertThatThrownBy(() -> service.prepareProductCreation(99L,
                    new FileUploadRequestDTO("img.jpg", "image/png", 1000L, null, null)))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("archivo más grande de 5MB: lanza ValidationException")
        void oversizedFile_throwsValidationException() {
            when(commercialDetailsRepository.existsByUser_Id(1L)).thenReturn(true);
            FileUploadRequestDTO metadata = new FileUploadRequestDTO("img.jpg", "image/png", 6L * 1024 * 1024, null, null);

            assertThatThrownBy(() -> service.prepareProductCreation(1L, metadata))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("content-type no permitido (ej. PDF): lanza ValidationException")
        void disallowedMimeType_throwsValidationException() {
            when(commercialDetailsRepository.existsByUser_Id(1L)).thenReturn(true);
            FileUploadRequestDTO metadata = new FileUploadRequestDTO("doc.pdf", "application/pdf", 1000L, null, null);

            assertThatThrownBy(() -> service.prepareProductCreation(1L, metadata))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    @DisplayName("confirmProductCreation")
    class ConfirmProductCreation {

        private ConfirmProductCreationRequestDTO requestWithPrice(BigDecimal price) {
            CreateProductRequestDTO data = new CreateProductRequestDTO();
            data.setName("Netflix");
            data.setDescription("1 mes premium");
            data.setProductCategoryId(3L);
            data.setPrice(price);
            data.setStockItems(List.of());
            return ConfirmProductCreationRequestDTO.builder().productAssetId(500L).productData(data).build();
        }

        private ProductImageAsset pendingAsset() {
            return ProductImageAsset.builder().id(500L).objectKey("products/1/img.jpg").sizeBytes(1000L)
                    .status(AssetStatus.PENDING).build();
        }

        @Test
        @DisplayName("flujo feliz: valida el asset en R2, arma el producto y lo persiste")
        void happyPath_persistsProduct() {
            CommercialDetails commercial = commercial(1L);
            ProductImageAsset asset = pendingAsset();
            Product mappedProduct = new Product();
            mappedProduct.setStockItems(List.of());
            mappedProduct.setPriceCents(1_500_000L); // simula lo que el mapper real produciría para $15.000 COP

            when(commercialDetailsRepository.findByUser_Id(1L)).thenReturn(Optional.of(commercial));
            when(productImageAssetRepository.findById(500L)).thenReturn(Optional.of(asset));
            when(r2Service.validateUploadedObject(eq(true), anyString(), anyLong(), anyLong(), anySet()))
                    .thenReturn(SupportedMimeType.IMAGE_PNG);
            when(productCategoryService.getById(3L)).thenReturn(new ProductCategory());
            when(productMapper.toProduct(any())).thenReturn(mappedProduct);
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId(77L);
                return p;
            });

            var response = service.confirmProductCreation(1L, requestWithPrice(BigDecimal.valueOf(15_000)));

            assertThat(response.getId()).isEqualTo(77L);
            verify(productImageAssetRepository, times(1)).save(asset); // guarda el asset ya vinculado al producto
            verify(assetOrphanedService, never()).markAdAssetsAsOrphanedByIds(any());
        }

        @Test
        @DisplayName("precio fuera de rango: lanza ValidationException y marca el asset como huérfano")
        void priceOutOfRange_throwsAndOrphansAsset() {
            CommercialDetails commercial = commercial(1L);
            ProductImageAsset asset = pendingAsset();

            when(commercialDetailsRepository.findByUser_Id(1L)).thenReturn(Optional.of(commercial));
            when(productImageAssetRepository.findById(500L)).thenReturn(Optional.of(asset));
            when(r2Service.validateUploadedObject(eq(true), anyString(), anyLong(), anyLong(), anySet()))
                    .thenReturn(SupportedMimeType.IMAGE_PNG);
            when(productCategoryService.getById(3L)).thenReturn(new ProductCategory());
            Product cheapMappedProduct = new Product();
            cheapMappedProduct.setPriceCents(50_000L); // simula el mapper real para $500 COP
            when(productMapper.toProduct(any())).thenReturn(cheapMappedProduct);

            // $500 COP está por debajo del mínimo configurado ($1.000 COP = 100.000 centavos)
            assertThatThrownBy(() -> service.confirmProductCreation(1L, requestWithPrice(BigDecimal.valueOf(500))))
                    .isInstanceOf(ValidationException.class);

            verify(assetOrphanedService).markAdAssetsAsOrphanedByIds(List.of(500L));
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("asset ya asociado a otro producto: lanza ValidationException y lo marca huérfano")
        void assetAlreadyLinked_throwsAndOrphansAsset() {
            CommercialDetails commercial = commercial(1L);
            ProductImageAsset asset = pendingAsset();
            asset.setProduct(new Product()); // ya vinculado

            when(commercialDetailsRepository.findByUser_Id(1L)).thenReturn(Optional.of(commercial));
            when(productImageAssetRepository.findById(500L)).thenReturn(Optional.of(asset));

            assertThatThrownBy(() -> service.confirmProductCreation(1L, requestWithPrice(BigDecimal.valueOf(15_000))))
                    .isInstanceOf(ValidationException.class);

            verify(assetOrphanedService).markAdAssetsAsOrphanedByIds(List.of(500L));
        }
    }

    // ─── Consultas ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getById / getByIdAndCommercialId")
    class GetById {

        @Test
        @DisplayName("producto inexistente: lanza ObjectNotFoundException de Hibernate")
        void notFound_throwsObjectNotFoundException() {
            when(productRepository.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getById(1L)).isInstanceOf(ObjectNotFoundException.class);
        }

        @Test
        @DisplayName("getByIdAndCommercialId: producto que no pertenece al commercial lanza ObjectNotFoundException")
        void wrongCommercial_throwsObjectNotFoundException() {
            when(productRepository.findByIdAndCommercialId(1L, 2L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getByIdAndCommercialId(1L, 2L))
                    .isInstanceOf(ObjectNotFoundException.class);
        }
    }

    // ─── Favoritos ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addFavorite")
    class AddFavorite {

        @Test
        @DisplayName("producto activo, sin favorito previo: lo agrega")
        void activeProductNotFavorited_addsIt() {
            Product active = product(10L, ProductStatus.ACTIVE);
            when(favoriteProductRepository.existsByConsumerIdAndProductId(1L, 10L)).thenReturn(false);
            when(productRepository.findById(10L)).thenReturn(Optional.of(active));
            when(consumerDetailsService.getConsumerById(1L)).thenReturn(new ConsumerDetails());

            service.addFavorite(1L, 10L);

            verify(favoriteProductRepository).save(any(FavoriteProduct.class));
        }

        @Test
        @DisplayName("ya está en favoritos: lanza FavoriteProductException")
        void alreadyFavorited_throwsFavoriteProductException() {
            when(favoriteProductRepository.existsByConsumerIdAndProductId(1L, 10L)).thenReturn(true);

            assertThatThrownBy(() -> service.addFavorite(1L, 10L)).isInstanceOf(FavoriteProductException.class);
            verify(favoriteProductRepository, never()).save(any());
        }

        @Test
        @DisplayName("producto inactivo: lanza FavoriteProductException")
        void inactiveProduct_throwsFavoriteProductException() {
            Product inactive = product(10L, ProductStatus.INACTIVE);
            when(favoriteProductRepository.existsByConsumerIdAndProductId(1L, 10L)).thenReturn(false);
            when(productRepository.findById(10L)).thenReturn(Optional.of(inactive));

            assertThatThrownBy(() -> service.addFavorite(1L, 10L)).isInstanceOf(FavoriteProductException.class);
        }

        @Test
        @DisplayName("id de consumidor o producto inválido (<=0): lanza IllegalArgumentException")
        void invalidIds_throwIllegalArgumentException() {
            assertThatThrownBy(() -> service.addFavorite(0L, 10L)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.addFavorite(1L, 0L)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("countFavoriteProductsByConsumerId")
    class CountFavorites {

        @Test
        @DisplayName("id inválido: lanza IllegalArgumentException")
        void invalidId_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.countFavoriteProductsByConsumerId(-1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("id válido: delega en el repositorio")
        void validId_delegatesToRepository() {
            when(favoriteProductRepository.countByConsumerId(1L)).thenReturn(5L);
            assertThat(service.countFavoriteProductsByConsumerId(1L)).isEqualTo(5L);
        }
    }

    // ─── Moderación por admin ───────────────────────────────────────────────

    @Nested
    @DisplayName("approveProductForAdmin")
    class ApproveProduct {

        @Test
        @DisplayName("producto PENDING: mueve la imagen a pública y lo activa")
        void pendingProduct_movesImageAndActivates() {
            Product pending = product(1L, ProductStatus.PENDING);
            AdminDetails admin = new AdminDetails();
            ProductImageAsset asset = ProductImageAsset.builder().objectKey("products/1/img.jpg").build();

            when(productRepository.findById(1L)).thenReturn(Optional.of(pending));
            when(productImageAssetRepository.findByProductId(1L)).thenReturn(Optional.of(asset));
            when(adminDetailsService.getById(5L)).thenReturn(admin);
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(productMapper.toProductResponseDTO(any())).thenReturn(new ProductResponseDTO());

            service.approveProductForAdmin(5L, 1L);

            assertThat(pending.getStatus()).isEqualTo(ProductStatus.ACTIVE);
            assertThat(pending.getApprovedBy()).isSameAs(admin);
            verify(r2Service).copyObject("private/products/1/img.jpg", "public/products/1/img.jpg");
        }

        @Test
        @DisplayName("producto que ya no está PENDING: lanza InvalidStatusException")
        void nonPendingProduct_throwsInvalidStatusException() {
            Product active = product(1L, ProductStatus.ACTIVE);
            when(productRepository.findById(1L)).thenReturn(Optional.of(active));

            assertThatThrownBy(() -> service.approveProductForAdmin(5L, 1L))
                    .isInstanceOf(InvalidStatusException.class);
        }
    }

    @Nested
    @DisplayName("rejectProductForAdmin")
    class RejectProduct {

        @Test
        @DisplayName("producto PENDING: lo rechaza con motivo y notifica al comercial")
        void pendingProduct_rejectsAndNotifies() {
            Product pending = product(1L, ProductStatus.PENDING);
            CommercialDetails commercial = commercial(1L);
            commercial.setId(1L);
            pending.setCommercial(commercial);

            when(productRepository.findById(1L)).thenReturn(Optional.of(pending));
            when(adminDetailsService.getById(5L)).thenReturn(new AdminDetails());
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            when(productMapper.toProductResponseDTO(any())).thenReturn(new ProductResponseDTO());

            service.rejectProductForAdmin(5L, 1L, "Imagen borrosa");

            assertThat(pending.getStatus()).isEqualTo(ProductStatus.REJECTED);
            assertThat(pending.getRejectionReason()).isEqualTo("Imagen borrosa");
            verify(notificationService).createInternalNotification(eq(1L), anyString(), eq("Imagen borrosa"), any());
        }

        @Test
        @DisplayName("producto que ya no está PENDING: lanza InvalidStatusException")
        void nonPendingProduct_throwsInvalidStatusException() {
            Product rejected = product(1L, ProductStatus.REJECTED);
            when(productRepository.findById(1L)).thenReturn(Optional.of(rejected));

            assertThatThrownBy(() -> service.rejectProductForAdmin(5L, 1L, "motivo"))
                    .isInstanceOf(InvalidStatusException.class);
        }
    }

    // ─── markProductAsReward (máximo 3 productos como premio de juego) ────────

    @Nested
    @DisplayName("markProductAsReward")
    class MarkProductAsReward {

        @Test
        @DisplayName("producto activo, comercial con menos de 3 premios: lo marca como premio")
        void activeProductUnderLimit_marksAsReward() {
            Product active = product(1L, ProductStatus.ACTIVE);
            CommercialDetails commercial = commercial(9L);
            commercial.setId(9L);
            active.setCommercial(commercial);
            active.setIsGameReward(false);

            when(productRepository.findById(1L)).thenReturn(Optional.of(active));
            when(productRepository.countGameRewards(9L)).thenReturn(2);

            service.markProductAsReward(9L, 1L);

            assertThat(active.getIsGameReward()).isTrue();
            verify(productRepository).save(active);
        }

        @Test
        @DisplayName("ya es premio: lo desmarca (toggle), sin validar el límite de 3")
        void alreadyReward_togglesOff() {
            Product active = product(1L, ProductStatus.ACTIVE);
            CommercialDetails commercial = commercial(9L);
            commercial.setId(9L);
            active.setCommercial(commercial);
            active.setIsGameReward(true);

            when(productRepository.findById(1L)).thenReturn(Optional.of(active));

            service.markProductAsReward(9L, 1L);

            assertThat(active.getIsGameReward()).isFalse();
            verify(productRepository, never()).countGameRewards(any());
        }

        @Test
        @DisplayName("comercial ya tiene 3 premios activos: lanza GameRewardException")
        void atLimit_throwsGameRewardException() {
            Product active = product(1L, ProductStatus.ACTIVE);
            CommercialDetails commercial = commercial(9L);
            commercial.setId(9L);
            active.setCommercial(commercial);
            active.setIsGameReward(false);

            when(productRepository.findById(1L)).thenReturn(Optional.of(active));
            when(productRepository.countGameRewards(9L)).thenReturn(3);

            assertThatThrownBy(() -> service.markProductAsReward(9L, 1L))
                    .isInstanceOf(GameRewardException.class);
        }

        @Test
        @DisplayName("producto de otro comercial: lanza InvalidRequestException")
        void notOwner_throwsInvalidRequestException() {
            Product active = product(1L, ProductStatus.ACTIVE);
            CommercialDetails commercial = commercial(9L);
            commercial.setId(9L);
            active.setCommercial(commercial);

            when(productRepository.findById(1L)).thenReturn(Optional.of(active));

            assertThatThrownBy(() -> service.markProductAsReward(123L, 1L))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("producto no activo (ej. PENDING): lanza InvalidStatusException")
        void nonActiveProduct_throwsInvalidStatusException() {
            Product pending = product(1L, ProductStatus.PENDING);
            CommercialDetails commercial = commercial(9L);
            commercial.setId(9L);
            pending.setCommercial(commercial);
            pending.setIsGameReward(false);

            when(productRepository.findById(1L)).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> service.markProductAsReward(9L, 1L))
                    .isInstanceOf(InvalidStatusException.class);
        }
    }

    // ─── Listados con URL de imagen resuelta según status ──────────────────────

    @Nested
    @DisplayName("resolución de imageUrl en listados (PENDING/REJECTED usan proxy privado)")
    class ImageUrlResolution {

        @Test
        @DisplayName("producto ACTIVE: usa la URL pública directa del CDN")
        void activeProduct_usesPublicCdnUrl() {
            Product active = product(1L, ProductStatus.ACTIVE);
            active.setImageAsset(ProductImageAsset.builder().objectKey("p1/img.jpg").build());

            when(productRepository.findById(1L)).thenReturn(Optional.of(active));
            ProductResponseDTO dto = new ProductResponseDTO();
            when(productMapper.toProductResponseDTO(active)).thenReturn(dto);

            service.detailProduct(1L);

            assertThat(dto.getImageUrl()).isEqualTo("https://cdn.verygana.com/public/p1/img.jpg");
        }

        @Test
        @DisplayName("producto PENDING: usa el endpoint proxy privado, no la URL pública del CDN")
        void pendingProduct_usesPrivateProxyUrl() {
            Product pending = product(1L, ProductStatus.PENDING);
            pending.setImageAsset(ProductImageAsset.builder().objectKey("p1/img.jpg").build());

            when(productRepository.findById(1L)).thenReturn(Optional.of(pending));
            ProductResponseDTO dto = new ProductResponseDTO();
            when(productMapper.toProductResponseDTO(pending)).thenReturn(dto);

            service.detailProduct(1L);

            assertThat(dto.getImageUrl()).isEqualTo("https://api.verygana.com/products/1/private-image");
        }
    }
}
