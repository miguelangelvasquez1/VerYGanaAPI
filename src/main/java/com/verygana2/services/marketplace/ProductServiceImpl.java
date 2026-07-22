package com.verygana2.services.marketplace;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.ObjectNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.FileUploadPermissionDTO;
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
import com.verygana2.exceptions.FavoriteProductException;
import com.verygana2.exceptions.GameRewardException;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.mappers.marketplace.ProductMapper;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.MediaType;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.models.Municipality;
import com.verygana2.models.TargetAudience;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.RequirePlanCapability;
import com.verygana2.models.marketplace.FavoriteProduct;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductImageAsset;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.marketplace.FavoriteProductRepository;
import com.verygana2.repositories.marketplace.ProductImageAssetRepository;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.repositories.marketplace.ProductStockRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.security.ProductCodeEncryptor;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.details.AdminDetailsService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.marketplace.ProductCategoryService;
import com.verygana2.services.interfaces.marketplace.ProductService;
import com.verygana2.storage.service.AssetOrphanedService;
import com.verygana2.storage.service.R2Service;
import com.verygana2.utils.validators.TargetAudienceAssembler;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    private final AdminDetailsService adminDetailsService;

    private final NotificationService notificationService;

    private final FavoriteProductRepository favoriteProductRepository;

    private final ProductCategoryService productCategoryService;

    private final ProductMapper productMapper;

    private final CommercialDetailsRepository commercialDetailsRepository;

    private final ConsumerDetailsService consumerDetailsService;

    private final ProductStockRepository productStockRepository;

    private final ProductImageAssetRepository productImageAssetRepository;

    private final R2Service r2Service;

    private final AssetOrphanedService assetOrphanedService;

    private final ProductCodeEncryptor productCodeEncryptor;

    private final TargetAudienceAssembler targetAudienceAssembler;

    @Value("${marketplace.max-product-price-cents:50000000}")
    private long maxProductPriceCents; // default $500.000 COP

    @Value("${marketplace.min-product-price-cents:100000}")
    private long minProductPriceCents; // default $1.000 COP

    @Value("${app.base-url}")
    private String appBaseUrl;

    private static final Set<SupportedMimeType> allowedImageMimeTypes = Set.of(SupportedMimeType.IMAGE_JPEG,
            SupportedMimeType.IMAGE_PNG,
            SupportedMimeType.IMAGE_WEBP);

    private static final int maxSizeBytesForImages = 5 * 1024 * 1024;

    @Override
    public AssetUploadPermissionDTO prepareProductCreation(Long commercialId,
            FileUploadRequestDTO productImageMetadata) {

        log.info("📋 Preparing product creation for commercial: {}", commercialId);

        // 1. Validar que el commercial existe
        if (!commercialDetailsRepository.existsByUser_Id(commercialId)) {
            throw new EntityNotFoundException("Commercial not found: " + commercialId);
        }

        // 2. Validar metadata del archivo
        validateFileMetadata(productImageMetadata);

        // 3. Crear el object key
        String objectKey = generateProductObjectKey(commercialId, productImageMetadata);

        // 4. Crear el asset
        ProductImageAsset asset = ProductImageAsset.builder().objectKey(objectKey)
                .sizeBytes(productImageMetadata.getSizeBytes())
                .status(AssetStatus.PENDING).uploadedAt(ZonedDateTime.now(ZoneOffset.UTC)).product(null).build();

        // 5. Guardar en la base de datos
        ProductImageAsset savedAsset = productImageAssetRepository.save(Objects.requireNonNull(asset));

        // 6. Generar pre-signed URL de R2
        FileUploadPermissionDTO permission = r2Service.generateUploadUrl(
                true,
                objectKey,
                productImageMetadata.getContentType());

        return AssetUploadPermissionDTO.builder().assetId(savedAsset.getId())
                .imagePermission(permission).build();
    }

    @Override
    public EntityCreatedResponseDTO confirmProductCreation(Long commercialId,
            ConfirmProductCreationRequestDTO request) {

        ProductImageAsset asset = null;

        try {
            CommercialDetails commercial = commercialDetailsRepository.findByUser_Id(commercialId)
                    .orElseThrow(() -> new EntityNotFoundException("Commercial not found: " + commercialId));

            asset = productImageAssetRepository
                    .findById(Objects.requireNonNull(request.getProductAssetId()))
                    .orElseThrow(() -> new ValidationException("Asset not found: " + request.getProductAssetId()));

            if (asset.getProduct() != null) {
                throw new ValidationException("Asset already associated to product: " + asset.getId());
            }

            if (asset.getStatus() != AssetStatus.PENDING) {
                throw new ValidationException(
                        "Asset invalid status to create a product: " + asset.getStatus());
            }

            log.info("Validating file in R2: {}", asset.getObjectKey());

            SupportedMimeType realMimeType = r2Service.validateUploadedObject(
                    true,
                    asset.getObjectKey(),
                    asset.getSizeBytes(),
                    maxSizeBytesForImages,
                    allowedImageMimeTypes);

            asset.setMimeType(realMimeType);
            asset.setStatus(AssetStatus.VALIDATED);

            ProductCategory category = productCategoryService.getById(request.getProductData().getProductCategoryId());
            Plan plan = commercial.getCurrentPlan();
            Product product = productMapper.toProduct(request.getProductData());
            validateProductPrice(product.getPriceCents());
            product.setCommercial(commercial);
            product.setProductCategory(category);
            product.setMaxKeysPct(plan.getMaxKeysPct());
            product.setTargetAudience(targetAudienceAssembler.build(request.getProductData().getTargeting()));

            // Asociar stockItems con el producto y cifrar sus códigos (nunca se
            // guardan en texto plano, ver ProductStockServiceImpl para el mismo patrón).
            if (product.getStockItems() != null) {
                product.getStockItems().forEach(stock -> {
                    stock.setProduct(product);
                    String plainCode = stock.getCode();
                    stock.setCode(productCodeEncryptor.encrypt(plainCode));
                    stock.setCodeHash(productCodeEncryptor.hash(plainCode));
                });
            }
            Product savedProduct = productRepository.save(Objects.requireNonNull(product));
            asset.setProduct(savedProduct);

            productImageAssetRepository.save(asset);

            return new EntityCreatedResponseDTO(savedProduct.getId(), "Product creation request sent succesfully",
                    Instant.now());

        } catch (Exception e) {
            if (asset != null) {
                log.error("product creation error, marking asset as orphan: {}", asset.getId());
                assetOrphanedService.markAdAssetsAsOrphanedByIds(List.of(asset.getId()));
            }
            throw e;
        }

    }

    private void validateProductPrice(long priceCents) {
        if (priceCents < minProductPriceCents) {
            throw new ValidationException(String.format(
                    "El precio mínimo permitido es $%,.0f COP. Recibido: $%,.0f COP",
                    minProductPriceCents / 100.0, priceCents / 100.0));
        }
        if (priceCents > maxProductPriceCents) {
            throw new ValidationException(String.format(
                    "El precio máximo permitido es $%,.0f COP. Recibido: $%,.0f COP",
                    maxProductPriceCents / 100.0, priceCents / 100.0));
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

    private String generateProductObjectKey(Long commercialId, FileUploadRequestDTO metadata) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(metadata.getOriginalFileName());

        return String.format("products/commercial-%d/%s-%s%s",
                commercialId, timestamp, uuid, extension);
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot);
    }

    @Override
    public Product getById(Long productId) {
        Objects.requireNonNull(productId, "The productId cannot be null");
        return productRepository.findById(productId).orElseThrow(
                () -> new ObjectNotFoundException("Product with id: " + productId + " not found ", Product.class));
    }

    @Override
    public Product getByIdAndCommercialId(Long productId, Long commercialId) {
        Objects.requireNonNull(productId, "The productId cannot be null");
        Objects.requireNonNull(commercialId, "The commercialId cannot be null");
        return productRepository.findByIdAndCommercialId(productId, commercialId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Product with id: " + productId + " and commercial id: " + commercialId + " not found ",
                        Product.class));
    }

    @Override
    public void delete(Long productId, Long commercialId) {
        Product product = getByIdAndCommercialId(productId, commercialId);
        productRepository.save(Objects.requireNonNull(product));
    }

    @Override
    public EntityUpdatedResponseDTO edit(Long productId, Long commercialId, UpdateProductRequestDTO request) {

        Product product = getByIdAndCommercialId(productId, commercialId);
        productMapper.updateProductFromRequest(request, product);
        validateProductPrice(product.getPriceCents());
        ProductCategory category = productCategoryService.getById(request.getProductCategoryId());
        product.setProductCategory(category);

        TargetAudience targetAudience = product.getTargetAudience();
        if (targetAudience == null) {
            targetAudience = new TargetAudience();
            product.setTargetAudience(targetAudience);
        }
        targetAudienceAssembler.applyTo(targetAudience, request.getTargeting());

        productRepository.save(product);

        return new EntityUpdatedResponseDTO(productId, "The product has been updated successfully", Instant.now());
    }

    @Override
    public AssetUploadPermissionDTO prepareProductImageUpdate(Long productId, Long commercialId,
            FileUploadRequestDTO imageMetadata) {

        if (!productRepository.existsByIdAndCommercialId(productId, commercialId)) {
            throw new ObjectNotFoundException(
                    "Product with id: " + productId + " and commercialId: " + commercialId + " not found",
                    Product.class);
        }

        validateFileMetadata(imageMetadata);

        String objectKey = generateProductObjectKey(commercialId, imageMetadata);

        ProductImageAsset newAsset = ProductImageAsset.builder()
                .objectKey(objectKey)
                .sizeBytes(imageMetadata.getSizeBytes())
                .status(AssetStatus.PENDING)
                .uploadedAt(ZonedDateTime.now())
                .product(null)
                .build();

        ProductImageAsset savedAsset = productImageAssetRepository.save(Objects.requireNonNull(newAsset));

        FileUploadPermissionDTO permission = r2Service.generateUploadUrl(
                true,
                objectKey,
                imageMetadata.getContentType());

        return AssetUploadPermissionDTO.builder()
                .assetId(savedAsset.getId())
                .imagePermission(permission)
                .build();
    }

    @Override
    public EntityUpdatedResponseDTO confirmProductImageUpdate(Long productId, Long commercialId,
            Long newAssetId) {

        ProductImageAsset newAsset = null;

        try {

            Product product = getByIdAndCommercialId(productId, commercialId);

            newAsset = productImageAssetRepository
                    .findById(Objects.requireNonNull(newAssetId))
                    .orElseThrow(() -> new ValidationException("Asset no encontrado: " + newAssetId));

            if (newAsset.getProduct() != null) {
                throw new ValidationException("Asset ya está asociado a un producto: " + newAsset.getId());
            }

            if (newAsset.getStatus() != AssetStatus.PENDING) {
                throw new ValidationException(
                        "Asset no está en estado válido: " + newAsset.getStatus());
            }

            SupportedMimeType realMimeType = r2Service.validateUploadedObject(
                    true,
                    newAsset.getObjectKey(),
                    newAsset.getSizeBytes(),
                    maxSizeBytesForImages,
                    allowedImageMimeTypes);

            newAsset.setMimeType(realMimeType);
            newAsset.setStatus(AssetStatus.VALIDATED);

            // Marcar el asset viejo como huérfano para que el job de limpieza lo elimine
            productImageAssetRepository.findByProductId(productId)
                    .ifPresent(oldAsset -> {
                        oldAsset.setProduct(null);
                        productImageAssetRepository.save(oldAsset);
                        assetOrphanedService.markAdAssetsAsOrphanedByIds(List.of(oldAsset.getId()));
                    });

            newAsset.setProduct(product);
            productImageAssetRepository.save(newAsset);

            return new EntityUpdatedResponseDTO(productId, "Product image updated successfully", Instant.now());

        } catch (Exception e) {
            if (newAsset != null) {
                log.error("Error actualizando imagen, marcando nuevo asset como huérfano: {}", newAsset.getId());
                assetOrphanedService.markAdAssetsAsOrphanedByIds(List.of(newAsset.getId()));
            }
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ProductSummaryResponseDTO> filterProducts(Long consumerId, String searchQuery,
            Long categoryId,
            Double minRating,
            BigDecimal maxPrice, Integer page,
            String sortBy, String sortDirection) {

        String sortField = validateAndGetSortField(sortBy);
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortField);

        int indexPage = (page != null && page >= 0) ? page : 0;
        Pageable pageable = PageRequest.of(indexPage, 20, sort);

        Long maxPriceCents = maxPrice != null ? maxPrice.multiply(BigDecimal.valueOf(100)).longValue() : null;

        // El municipio del consumidor solo prioriza el orden de resultados, nunca
        // excluye productos (ver TargetAudienceAssembler/plan de sectorización).
        ConsumerDetails consumer = consumerDetailsService.getConsumerById(consumerId);
        Municipality municipality = consumer.getMunicipality();

        PagedResponse<Product> productPage = PagedResponse
                .from(productRepository.searchProducts(searchQuery, categoryId, minRating, maxPriceCents,
                        municipality, pageable));

        return productPage.map(product -> {
            ProductSummaryResponseDTO dto = productMapper.toProductSummaryResponseDTO(product);
            dto.setImageUrl(resolveImageUrl(product));
            return dto;
        });

    }

    private String validateAndGetSortField(String sortBy) {
        Set<String> allowedFields = Set.of(
                "price",
                "averageRate",
                "createdAt");

        if (sortBy != null && allowedFields.contains(sortBy)) {
            return sortBy;
        }

        return "createdAt";
    }

    @Override
    public ProductResponseDTO detailProduct(Long productId) {
        Product product = getById(productId);
        ProductResponseDTO response = productMapper.toProductResponseDTO(product);
        response.setImageUrl(resolveImageUrl(product));
        return response;
    }

    // Métodos para Commercial
    @Override
    public PagedResponse<ProductSummaryResponseDTO> getCommercialProducts(Long commercialId, Integer page) {

        if (!commercialDetailsRepository.existsByUser_Id(commercialId)) {
            throw new ObjectNotFoundException("Commercial with id:" + commercialId + " not found ",
                    CommercialDetails.class);
        }

        Integer pageIndex = (page != null && page >= 0) ? page : 0;
        Pageable pageable = PageRequest.of(pageIndex, 20, Sort.Direction.DESC, "createdAt");
        PagedResponse<Product> products = PagedResponse
                .from(productRepository.findByCommercialId(commercialId, pageable));
        return products.map(product -> {
            ProductSummaryResponseDTO dto = productMapper.toProductSummaryResponseDTO(product);
            dto.setImageUrl(resolveImageUrl(product));
            return dto;
        });
    }

    @Override
    public Integer getTotalCommercialProducts(Long commercialId, ProductStatus status) {
        return productRepository.countCommercialProducts(commercialId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ProductSummaryResponseDTO> getFavorites(Long consumerId, Integer page) {
        Pageable pageable = PageRequest.of(page, 10, Sort.Direction.DESC, "createdAt");
        PagedResponse<FavoriteProduct> favorites = PagedResponse
                .from(favoriteProductRepository.findByConsumerIdWithActiveProducts(consumerId, pageable));
        return favorites.map(productMapper::toProductSummaryResponseDTO);
    }

    @Override
    public void addFavorite(Long consumerId, Long productId) {

        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id cannot be null");
        }

        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product id cannot be null");
        }

        if (favoriteProductRepository.existsByConsumerIdAndProductId(consumerId, productId)) {
            throw new FavoriteProductException("this product already exists in your favorite products list");
        }
        Product product = getById(productId);

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new FavoriteProductException("Inactive product cannot be added");
        }

        ConsumerDetails consumer = consumerDetailsService.getConsumerById(consumerId);

        FavoriteProduct favorite = new FavoriteProduct();
        favorite.setConsumer(consumer);
        favorite.setProduct(product);

        favoriteProductRepository.save(favorite);

    }

    @Override
    @SuppressWarnings("null")
    public void removeFavorite(Long consumerId, Long productId) {

        FavoriteProduct productToDelete = favoriteProductRepository.findByConsumerIdAndProductId(consumerId, productId)
                .orElseThrow(() -> new ObjectNotFoundException("Favorite product with productId :" + productId
                        + " and consumer id : " + consumerId + " not found ", FavoriteProduct.class));

        favoriteProductRepository.delete(productToDelete);

    }

    @Override
    public ProductEditInfoResponseDTO getProductEditInfo(Long productId, Long commercialId) {

        Product product = getByIdAndCommercialId(productId, commercialId);

        ProductEditInfoResponseDTO dto = productMapper.toProductEditInfoDTO(product);
        dto.setImageUrl(resolveImageUrl(product));

        // Agregar información de stock
        dto.setTotalStockItems(product.getStockItems() != null ? product.getStockItems().size() : 0);
        dto.setAvailableStockItems(
                productStockRepository.countByProductIdAndStatus(productId, StockStatus.AVAILABLE));

        return dto;
    }

    @Override
    public Long countFavoriteProductsByConsumerId(Long consumerId) {
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        return favoriteProductRepository.countByConsumerId(consumerId);
    }

    @Override
    public PagedResponse<ProductSummaryResponseDTO> getAllProductsForAdmin(ProductStatus status, String search, Pageable pageable) {
        PagedResponse<Product> products = PagedResponse
                .from(productRepository.findAllProductsForAdmin(status, search, pageable));
        return products.map(product -> {
            ProductSummaryResponseDTO dto = productMapper.toProductSummaryResponseDTO(product);
            dto.setImageUrl(resolveImageUrl(product));
            return dto;
        });
    }

    @Override
    public ProductResponseDTO approveProductForAdmin(Long adminId, Long productId) {
        log.info("Admin {} approving product {}", adminId, productId);

        Product product = getById(productId);

        if (product.getStatus() != ProductStatus.PENDING) {
            throw new InvalidStatusException("Only pending products can be approved");
        }

        ProductImageAsset imageAsset = productImageAssetRepository
                .findByProductId(productId)
                .orElseThrow(() -> new ValidationException("Product has no image asset"));

        String privateKey = "private/" + imageAsset.getObjectKey();
        String publicKey = "public/" + imageAsset.getObjectKey();

        r2Service.copyObject(privateKey, publicKey);

        try {
            r2Service.deleteObject(privateKey);

        } catch (Exception e) {
            log.warn("couldn't eliminate private object {}, it requires manual clean", privateKey);
            r2Service.markAsOrphan(privateKey);
        }

        AdminDetails admin = adminDetailsService.getById(adminId);
        product.setApprovedBy(admin);
        product.setApprovedAt(ZonedDateTime.now(ZoneOffset.UTC));
        product.setStatus(ProductStatus.ACTIVE);
        productRepository.save(product);

        log.info("Product {} approved and image moved to public", productId);

        return productMapper.toProductResponseDTO(product);
    }

    @Override
    public ProductResponseDTO rejectProductForAdmin(Long adminId, Long productId, String reason) {
        log.info("Admin {} rejecting product {}", adminId, productId);

        Product product = getById(productId);

        if (!product.getStatus().equals(ProductStatus.PENDING)) {
            throw new InvalidStatusException("Only pending products can be rejected");
        }

        AdminDetails admin = adminDetailsService.getById(adminId);
        product.setRejectedBy(admin);
        product.setRejectedAt(ZonedDateTime.now(ZoneOffset.UTC));
        product.setRejectionReason(reason);
        product.setStatus(ProductStatus.REJECTED);
        productRepository.save(product);

        log.info("Product {} rejected succesfully", productId);

        notificationService.createInternalNotification(product.getCommercial().getId(),
                "Solicitud de creacion de producto rechazada", reason, Instant.now());
        return productMapper.toProductResponseDTO(product);
    }

    @Override
    @Transactional(readOnly = true)
    public void streamPrivateProductImage(Long productId, HttpServletResponse response) throws IOException {
        Product product = getById(productId);
        if (product.getImageAsset() == null) {
            throw new EntityNotFoundException("Product has no image asset: " + productId);
        }
        String objectKey = product.getImageAsset().getObjectKey();
        log.info("Streaming private image: productId={}, objectKey=private/{}", productId, objectKey);

        try (var stream = r2Service.getPrivateObjectStream(objectKey)) {
            String contentType = stream.response().contentType();
            response.setContentType(contentType != null ? contentType : "image/jpeg");
            Long contentLength = stream.response().contentLength();
            if (contentLength != null && contentLength > 0) {
                response.setContentLengthLong(contentLength);
            }
            response.setHeader("Cache-Control", "private, max-age=300");
            stream.transferTo(response.getOutputStream());
        }
    }

    private String resolveImageUrl(Product product) {
        if (product.getImageAsset() == null) {
            return null;
        }

        if (product.getStatus() == ProductStatus.PENDING || product.getStatus() == ProductStatus.REJECTED) {
            return appBaseUrl + "/products/" + product.getId() + "/private-image";
        }

        return product.getImageUrl();
    }

    @Override
    @RequirePlanCapability({RequirePlanCapability.Capability.CAN_USE_GAMES})
    public void markProductAsReward(Long commercialId, Long productId) {
        Product product = getById(productId);

        if (!commercialId.equals(product.getCommercial().getId())) {
            throw new InvalidRequestException("Product does not belong to this commercial");
        }

        if (Boolean.TRUE.equals(product.getIsGameReward())) {
            product.setIsGameReward(false);
            product.setGameRewardAutoDisabled(false);
            productRepository.save(product);
            return;
        }

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new InvalidStatusException("Only active products can be marked as game rewards");
        }

        if (productRepository.countGameRewards(commercialId) >= 3) {
            throw new GameRewardException("You already have 3 active products marked as game rewards");
        }

        product.setIsGameReward(true);
        product.setGameRewardAutoDisabled(false);
        productRepository.save(product);
    }

}
