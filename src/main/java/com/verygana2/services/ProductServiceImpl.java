package com.verygana2.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import org.hibernate.ObjectNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.product.requests.CreateProductRequestDTO;
import com.verygana2.dtos.product.requests.UpdateProductRequestDTO;
import com.verygana2.dtos.product.responses.ProductEditInfoResponseDTO;
import com.verygana2.dtos.product.responses.ProductResponseDTO;
import com.verygana2.dtos.product.responses.ProductSummaryResponseDTO;
import com.verygana2.exceptions.FavoriteProductException;
import com.verygana2.mappers.products.ProductMapper;
import com.verygana2.models.enums.StockStatus;
import com.verygana2.models.products.FavoriteProduct;
import com.verygana2.models.products.Product;
import com.verygana2.models.products.ProductCategory;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.models.userDetails.SellerDetails;
import com.verygana2.repositories.FavoriteProductRepository;
import com.verygana2.repositories.ProductRepository;
import com.verygana2.repositories.ProductStockRepository;
import com.verygana2.services.interfaces.ProductCategoryService;
import com.verygana2.services.interfaces.ProductService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.details.SellerDetailsService;
import com.verygana2.storage.dto.UploadResult;
import com.verygana2.storage.service.CloudStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    private final FavoriteProductRepository favoriteProductRepository;

    private final ProductCategoryService productCategoryService;

    private final ProductMapper productMapper;

    private final SellerDetailsService sellerDetailsService;

    private final ConsumerDetailsService consumerDetailsService;

    private final CloudStorageService cloudStorageService;

    private final ProductStockRepository productStockRepository;

    @Override
    public EntityCreatedResponseDTO create(CreateProductRequestDTO request, Long sellerId,
            MultipartFile productImage) {
        SellerDetails seller = sellerDetailsService.getSellerById(sellerId);
        ProductCategory category = productCategoryService.getById(request.getProductCategoryId());

        Product product = productMapper.toProduct(request);
        product.setSeller(seller);
        product.setProductCategory(category);

        UploadResult uploadResult = cloudStorageService.uploadFile(productImage, "products", null);

        product.setImageUrl(uploadResult.getSecureUrl());

        // Asociar stockItems con el producto
        if (product.getStockItems() != null) {
            product.getStockItems().forEach(stock -> stock.setProduct(product));
        }
        Product savedProduct = productRepository.save(product);

        return new EntityCreatedResponseDTO(savedProduct.getId(), "Product created succesfully", Instant.now());
    }

    @Override
    public Product getById(Long productId) {
        Objects.requireNonNull(productId, "The productId cannot be null");
        return productRepository.findById(productId).orElseThrow(
                () -> new ObjectNotFoundException("Product with id: " + productId + " not found", Product.class));
    }

    @Override
    public void delete(Long productId, Long sellerId) {
        if (!productRepository.existsByIdAndSellerId(productId, sellerId)) {
            throw new ObjectNotFoundException(
                    "Product with id: " + productId + " and sellerId: " + sellerId + " not found", Product.class);
        }
        Product product = getById(productId);
        product.setActive(false);
        productRepository.save(product);
    }

    @Override
    public void deleteForAdmin(Long productId) {
        // send a notification to product's owner with the reason of product elimination
        Product product = getById(productId);
        product.setActive(false);
        productRepository.save(product);
    }

    @Override
    public EntityUpdatedResponseDTO edit(Long productId, Long sellerId,
            UpdateProductRequestDTO updateProductRequestDTO, MultipartFile productImage) {
        if (!productRepository.existsByIdAndSellerId(productId, sellerId)) {
            throw new ObjectNotFoundException(
                    "Product with id: " + productId + " and sellerId: " + sellerId + " not found", Product.class);
        }
        Product product = getById(productId);
        String oldImageUrl = product.getImageUrl();
        productMapper.updateProductFromRequest(updateProductRequestDTO, product);

        ProductCategory category = productCategoryService.getById(updateProductRequestDTO.getProductCategoryId());
        product.setProductCategory(category);

        if (productImage != null && !productImage.isEmpty()) {
            try {
                UploadResult uploadResult = cloudStorageService.uploadFile(productImage, "products", null);
                product.setImageUrl(uploadResult.getSecureUrl());
                if (oldImageUrl != null && !oldImageUrl.isEmpty()) {
                    String oldPublicId = extractPublicIdFromUrl(oldImageUrl);
                    if (oldPublicId != null) {
                        cloudStorageService.deleteFile(oldPublicId, "image");
                    }
                }
            } catch (Exception e) {
                log.error("Error trying to update the product image: {}", e.getMessage(), e);
                throw new RuntimeException("Error processing the product image", e);
            }
        }

        productRepository.save(product);
        return new EntityUpdatedResponseDTO(productId, "The product has been updated succesfully", Instant.now());
    }

    /**
     * Extrae el publicId de una URL de Cloudinary
     * Ejemplo:
     * https://res.cloudinary.com/demo/image/upload/v1234567890/products/abc123.jpg
     * Retorna: products/abc123
     */
    private String extractPublicIdFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }

        try {
            // Buscar el patrón /upload/ o /upload/v[version]/
            int uploadIndex = imageUrl.indexOf("/upload/");
            if (uploadIndex == -1) {
                return null;
            }

            // Obtener la parte después de /upload/
            String afterUpload = imageUrl.substring(uploadIndex + 8); // 8 = length of "/upload/"

            // Si hay una versión (v1234567890/), saltarla
            if (afterUpload.startsWith("v") && afterUpload.contains("/")) {
                afterUpload = afterUpload.substring(afterUpload.indexOf("/") + 1);
            }

            // Remover la extensión del archivo
            int lastDotIndex = afterUpload.lastIndexOf(".");
            if (lastDotIndex > 0) {
                afterUpload = afterUpload.substring(0, lastDotIndex);
            }

            return afterUpload;

        } catch (Exception e) {
            log.error("Error al extraer publicId de la URL: {}", imageUrl, e);
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ProductSummaryResponseDTO> getAllProducts(Integer page) {
        Pageable pageable = PageRequest.of(page, 20, Direction.DESC, "createdAt");
        PagedResponse<Product> activeProducts = PagedResponse.from(productRepository.findAllActiveProducts(pageable));
        return activeProducts.map(productMapper::toProductSummaryResponseDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ProductSummaryResponseDTO> filterProducts(String searchQuery, Long categoryId,
            Double minRating,
            BigDecimal maxPrice, Integer page,
            String sortBy, String sortDirection) {

        String sortField = validateAndGetSortField(sortBy);
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortField);

        int indexPage = (page != null && page >= 0) ? page : 0;
        Pageable pageable = PageRequest.of(indexPage, 20, sort);

        PagedResponse<Product> productPage = PagedResponse
                .from(productRepository.searchProducts(searchQuery, categoryId, minRating, maxPrice,
                        pageable));

        return productPage.map(productMapper::toProductSummaryResponseDTO);

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
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("the product id must be positive");
        }
        Product product = productRepository.findById(productId).orElseThrow(
                () -> new ObjectNotFoundException("the product with id: " + productId + " not found", Product.class));
        ProductResponseDTO response = productMapper.toProductResponseDTO(product);
        return response;
    }

    // Métodos para Seller
    @Override
    public PagedResponse<ProductSummaryResponseDTO> getSellerProducts(Long sellerId, Integer page) {

        if (!sellerDetailsService.existsSellerById(sellerId)) {
            throw new ObjectNotFoundException("Seller with id:" + sellerId + " not found", SellerDetails.class);
        }

        Integer pageIndex = (page != null && page >= 0) ? page : 0;
        Pageable pageable = PageRequest.of(pageIndex, 20, Sort.Direction.DESC, "createdAt");
        PagedResponse<Product> products = PagedResponse.from(productRepository.findBySellerId(sellerId, pageable));
        return products.map(productMapper::toProductSummaryResponseDTO);
    }

    @Override
    public Long getTotalSellerProducts(Long sellerId) {
        return productRepository.countSellerProducts(sellerId);
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

        if (!product.isActive()) {
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
    public ProductEditInfoResponseDTO getProductEditInfo(Long productId, Long sellerId) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product id must be positive");
        }

        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("Seller id must be positive");
        }

        Product product = productRepository.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Product with id: " + productId + " and seller id: " + sellerId + " not found ", Product.class));

        ProductEditInfoResponseDTO dto = productMapper.toProductEditInfoDTO(product);

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

}
