package com.verygana2.controllers;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.product.requests.CreateProductRequestDTO;
import com.verygana2.dtos.product.requests.ProductStockRequestDTO;
import com.verygana2.dtos.product.requests.UpdateProductRequestDTO;
import com.verygana2.dtos.product.responses.BulkStockResponseDTO;
import com.verygana2.dtos.product.responses.ProductEditInfoResponseDTO;
import com.verygana2.dtos.product.responses.ProductResponseDTO;
import com.verygana2.dtos.product.responses.ProductStockResponseDTO;
import com.verygana2.dtos.product.responses.ProductSummaryResponseDTO;
import com.verygana2.models.enums.StockStatus;
import com.verygana2.services.interfaces.ProductService;
import com.verygana2.services.interfaces.ProductStockService;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    private final ProductStockService productStockService;

    /**
     * Crear un producto
     */
    @PostMapping("/create")
    @PreAuthorize("hasRole('ROLE_SELLER')")
    public ResponseEntity<EntityCreatedResponseDTO> createProduct(@Valid @RequestPart("product") String productJson,
            @RequestPart("productImage") MultipartFile productImage, @AuthenticationPrincipal Jwt jwt) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());

            CreateProductRequestDTO request = objectMapper.readValue(productJson,
                    CreateProductRequestDTO.class);

            // Validación manual
            ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<CreateProductRequestDTO>> violations = validator.validate(request);

            if (!violations.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (ConstraintViolation<CreateProductRequestDTO> violation : violations) {
                    sb.append(violation.getMessage()).append("; ");
                }
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, sb.toString());
            }

            Long userId = jwt.getClaim("userId");
            EntityCreatedResponseDTO response = productService.create(request, userId, productImage);
            return ResponseEntity.created(Objects.requireNonNull(URI.create("/products/" + response.getId())))
                    .body(response);

        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON format", e);
        }
    }

    /**
     * Eliminar un producto
     */
    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('ROLE_SELLER')")
    public ResponseEntity<Void> deleteProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        Long userId = jwt.getClaim("userId");
        productService.delete(productId, userId);
        return ResponseEntity.noContent().build();

    }

    /**
     * ELiminar un producto siendo el admin
     */
    @DeleteMapping("/delete/admin/{productId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteProductForAdmin(@PathVariable Long productId) {
        productService.deleteForAdmin(productId);
        return ResponseEntity.noContent().build();

    }

    /**
     * obtener la informacion guardada de un producto para editarlo
     */
    @GetMapping("/edit/{productId}")
    @PreAuthorize("hasRole('ROLE_SELLER')")
    public ResponseEntity<ProductEditInfoResponseDTO> getProductEditInfo(@PathVariable Long productId,
            @AuthenticationPrincipal Jwt jwt) {
        Long sellerId = jwt.getClaim("userId");
        return ResponseEntity.ok(productService.getProductEditInfo(productId, sellerId));
    }

    /**
     * Editar un producto
     */
    @PutMapping("/edit/{productId}")
    @PreAuthorize("hasRole('ROLE_SELLER')")
    public ResponseEntity<EntityUpdatedResponseDTO> editProduct(
            @Valid @RequestPart("product") String productJson,
            @RequestPart(value = "productImage", required = false) MultipartFile productImage,
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productId) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());

            UpdateProductRequestDTO request = objectMapper.readValue(productJson,
                    UpdateProductRequestDTO.class);

            // Validación manual
            ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<UpdateProductRequestDTO>> violations = validator.validate(request);

            if (!violations.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (ConstraintViolation<UpdateProductRequestDTO> violation : violations) {
                    sb.append(violation.getMessage()).append("; ");
                }
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, sb.toString());
            }

            Long sellerId = jwt.getClaim("userId");
            EntityUpdatedResponseDTO response = productService.edit(productId, sellerId, request, productImage);
            return ResponseEntity.ok(response);

        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON format", e);
        }
    }

    /**
     * Obtener el listado de codigos registrados de un producto
     */
    @GetMapping("/{productId}/stock")
    @PreAuthorize("hasRole('ROLE_SELLER')")
    public ResponseEntity<PagedResponse<ProductStockResponseDTO>> getProductStock(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) StockStatus status,
            @AuthenticationPrincipal Jwt jwt) {
        Long sellerId = jwt.getClaim("userId");

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        return ResponseEntity.ok(productStockService.getProductStock(productId, sellerId, search, status, pageable));
    }

    /**
     * Agregar un nuevo código de stock
     */
    @PostMapping("/{productId}/stock")
    @PreAuthorize("hasRole('ROLE_SELLER')")
    public ResponseEntity<ProductStockResponseDTO> addStockItem(
            @PathVariable Long productId,
            @RequestBody @Valid ProductStockRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) {
        Long sellerId = jwt.getClaim("userId");
        ProductStockResponseDTO created = productStockService.addStockItem(productId, sellerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Editar un código de stock específico
     */
    @PutMapping("/{productId}/stock/{stockId}")
    @PreAuthorize("hasRole('ROLE_SELLER')")
    public ResponseEntity<ProductStockResponseDTO> updateStockItem(
            @PathVariable Long productId,
            @PathVariable Long stockId,
            @RequestBody @Valid ProductStockRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) {
        Long sellerId = jwt.getClaim("userId");
        ProductStockResponseDTO updated = productStockService.updateStockItem(productId, stockId, sellerId, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Eliminar un código de stock específico
     */
    @DeleteMapping("/{productId}/stock/{stockId}")
    @PreAuthorize("hasRole('ROLE_SELLER')")
    public ResponseEntity<Void> deleteStockItem(
            @PathVariable Long productId,
            @PathVariable Long stockId,
            @AuthenticationPrincipal Jwt jwt) {
        Long sellerId = jwt.getClaim("userId");
        productStockService.deleteStockItem(productId, stockId, sellerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Agregar múltiples códigos de stock de una vez
     */
    @PostMapping("/{productId}/stock/bulk")
    @PreAuthorize("hasRole('ROLE_SELLER')")
    public ResponseEntity<BulkStockResponseDTO> addBulkStockItems(
            @PathVariable Long productId,
            @RequestBody @Valid List<ProductStockRequestDTO> requests,
            @AuthenticationPrincipal Jwt jwt) {
        Long sellerId = jwt.getClaim("userId");
        BulkStockResponseDTO response = productStockService.addBulkStockItems(productId, sellerId, requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Cargar productos
     */
    @GetMapping
    public ResponseEntity<PagedResponse<ProductSummaryResponseDTO>> loadProducts(
            @RequestParam(defaultValue = "0") Integer page) {
        PagedResponse<ProductSummaryResponseDTO> response = productService.getAllProducts(page);
        return ResponseEntity.ok(response);
    }

    /**
     * Buscar productos con filtros
     */
    @GetMapping("/filter")
    public ResponseEntity<PagedResponse<ProductSummaryResponseDTO>> searchProducts(
            @RequestParam(required = false) String searchQuery,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) BigDecimal maxPrice, @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        PagedResponse<ProductSummaryResponseDTO> response = productService.filterProducts(searchQuery, categoryId,
                minRating,
                maxPrice, page, sortBy, sortDirection);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtener la vista detallada de un producto
     */
    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponseDTO> getProductDetail(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.detailProduct(productId));
    }

    /**
     * Obtener los productos de un vendedor
     */
    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<PagedResponse<ProductSummaryResponseDTO>> getSellerProducts(@PathVariable Long sellerId,
            @RequestParam(defaultValue = "0") Integer page) {
        return ResponseEntity.ok(productService.getSellerProducts(sellerId, page));
    }

    /**
     * Obtener los productos de un vendedor siendo el vendedor
     */
    @GetMapping("/myProducts")
    @PreAuthorize("hasRole('ROLE_SELLER')")
    public ResponseEntity<PagedResponse<ProductSummaryResponseDTO>> getMyProducts(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") Integer page) {
        Long sellerId = jwt.getClaim("userId");
        return ResponseEntity.ok(productService.getSellerProducts(sellerId, page));
    }

    /**
     * Obtener el numero de productos activos que tiene un vendedor
     */
    @GetMapping("/totalProducts")
    @PreAuthorize("hasRole('ROLE_SELLER')")
    public ResponseEntity<Long> getTotalSellerProducts(@AuthenticationPrincipal Jwt jwt) {
        Long sellerId = jwt.getClaim("userId");
        return ResponseEntity.ok(productService.getTotalSellerProducts(sellerId));
    }

    /**
     * Obtener la lista de productos favoritos de un usuario
     */
    @GetMapping("/favorites")
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    public ResponseEntity<PagedResponse<ProductSummaryResponseDTO>> getFavorites(
            @RequestParam(defaultValue = "0") Integer page,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        PagedResponse<ProductSummaryResponseDTO> response = productService.getFavorites(userId, page);
        return ResponseEntity.ok(response);
    }

    /**
     * Agregar un producto a la lista de favoritos
     */
    @PostMapping("{productId}/favorites")
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    public ResponseEntity<Void> addToFavorites(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        Long userId = jwt.getClaim("userId");
        productService.addFavorite(userId, productId);
        return ResponseEntity.ok().build();
    }

    /**
     * Remover un producto a la lista de favoritos
     */
    @DeleteMapping("{productId}/favorites")
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    public ResponseEntity<Void> removeFromFavorites(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        Long userId = jwt.getClaim("userId");
        productService.removeFavorite(userId, productId);
        return ResponseEntity.noContent().build();
    }

}