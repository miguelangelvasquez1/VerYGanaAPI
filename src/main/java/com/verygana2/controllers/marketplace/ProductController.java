package com.verygana2.controllers.marketplace;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.AssetUploadPermissionDTO;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.product.requests.ConfirmProductCreationRequestDTO;
import com.verygana2.dtos.product.requests.ConfirmProductImageUpdateRequestDTO;
import com.verygana2.dtos.product.requests.ProductStockRequestDTO;
import com.verygana2.dtos.product.requests.UpdateProductRequestDTO;
import com.verygana2.dtos.product.responses.BulkStockResponseDTO;
import com.verygana2.dtos.product.responses.ProductEditInfoResponseDTO;
import com.verygana2.dtos.product.responses.ProductResponseDTO;
import com.verygana2.dtos.product.responses.ProductStockResponseDTO;
import com.verygana2.dtos.product.responses.ProductSummaryResponseDTO;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.enums.marketplace.StockStatus;
import com.verygana2.services.interfaces.marketplace.ProductService;
import com.verygana2.services.interfaces.marketplace.ProductStockService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    private final ProductStockService productStockService;

    /**
     * Preparar la creacion de un producto
     */
    @PostMapping("/prepare")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<AssetUploadPermissionDTO> prepareProductCreation(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FileUploadRequestDTO productImageMetaData) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(productService.prepareProductCreation(commercialId, productImageMetaData));
    }

    /*
     * Crear producto (Luego de confirmar subida a R2)
     */

    @PostMapping("/confirm")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<EntityCreatedResponseDTO> confirmProductCreation(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConfirmProductCreationRequestDTO request) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(productService.confirmProductCreation(commercialId, request));
    }

    /**
     * Eliminar un producto
     */
    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<Void> deleteProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        Long commercialId = jwt.getClaim("userId");
        productService.delete(productId, commercialId);
        return ResponseEntity.noContent().build();

    }

    /**
     * obtener la informacion guardada de un producto para editarlo
     */
    @GetMapping("/edit/{productId}")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<ProductEditInfoResponseDTO> getProductEditInfo(@PathVariable Long productId,
            @AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(productService.getProductEditInfo(productId, commercialId));
    }

    /**
     * Editar un producto
     */
    @PatchMapping("/{productId}")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<EntityUpdatedResponseDTO> editProduct(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productId, @Valid @RequestBody UpdateProductRequestDTO request) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(productService.edit(productId, commercialId, request));
    }

    /*
     * Paso 1 - Preparar actualización de imagen
     */

    @PostMapping("/{productId}/image/prepare")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<AssetUploadPermissionDTO> prepareProductImageUpdate(
            @PathVariable Long productId,
            @RequestBody @Valid FileUploadRequestDTO imageMetadata,
            @AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(productService.prepareProductImageUpdate(productId, commercialId, imageMetadata));
    }

    /**
     * Paso 2 - Confirmar actualización de imagen (luego de subir a R2)
     */

    @PostMapping("/{productId}/image/confirm")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<EntityUpdatedResponseDTO> confirmProductImageUpdate(
            @PathVariable Long productId,
            @RequestBody @Valid ConfirmProductImageUpdateRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(
                productService.confirmProductImageUpdate(productId, commercialId, request.getNewAssetId()));
    }

    /**
     * Obtener el listado de codigos registrados de un producto
     */
    @GetMapping("/{productId}/stock")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<PagedResponse<ProductStockResponseDTO>> getProductStock(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) StockStatus status,
            @AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        return ResponseEntity
                .ok(productStockService.getProductStock(productId, commercialId, search, status, pageable));
    }

    /**
     * Agregar un nuevo código de stock
     */
    @PostMapping("/{productId}/stock")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<ProductStockResponseDTO> addStockItem(
            @PathVariable Long productId,
            @RequestBody @Valid ProductStockRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        ProductStockResponseDTO created = productStockService.addStockItem(productId, commercialId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Editar un código de stock específico
     */
    @PutMapping("/{productId}/stock/{stockId}")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<ProductStockResponseDTO> updateStockItem(
            @PathVariable Long productId,
            @PathVariable Long stockId,
            @RequestBody @Valid ProductStockRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        ProductStockResponseDTO updated = productStockService.updateStockItem(productId, stockId, commercialId,
                request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Eliminar un código de stock específico
     */
    @DeleteMapping("/{productId}/stock/{stockId}")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<Void> deleteStockItem(
            @PathVariable Long productId,
            @PathVariable Long stockId,
            @AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        productStockService.deleteStockItem(productId, stockId, commercialId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Agregar múltiples códigos de stock de una vez
     */
    @PostMapping("/{productId}/stock/bulk")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<BulkStockResponseDTO> addBulkStockItems(
            @PathVariable Long productId,
            @RequestBody @Valid List<ProductStockRequestDTO> requests,
            @AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        BulkStockResponseDTO response = productStockService.addBulkStockItems(productId, commercialId, requests);
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
    @GetMapping("/commercial/{commercialId}")
    public ResponseEntity<PagedResponse<ProductSummaryResponseDTO>> getCommercialProducts(
            @PathVariable Long commercialId,
            @RequestParam(defaultValue = "0") Integer page) {
        return ResponseEntity.ok(productService.getCommercialProducts(commercialId, page));
    }

    /**
     * Obtener los productos de un vendedor siendo el vendedor
     */
    @GetMapping("/my-products")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<PagedResponse<ProductSummaryResponseDTO>> getMyProducts(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") Integer page) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(productService.getCommercialProducts(commercialId, page));
    }

    /**
     * Obtener el numero de productos activos que tiene un vendedor (ACTIVOS O PENDIENTES)
     */
    @GetMapping("/total-products")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<Long> getTotalCommercialProducts(@AuthenticationPrincipal Jwt jwt, @RequestParam("status") ProductStatus status) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(productService.getTotalCommercialProducts(commercialId, status));
    }

    /**
     * Obtener la lista de productos favoritos de un usuario
     */
    @GetMapping("/favorites")
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    public ResponseEntity<PagedResponse<ProductSummaryResponseDTO>> getFavorites(
            @RequestParam(defaultValue = "0") Integer page,
            @AuthenticationPrincipal Jwt jwt) {

        Long consumerId = jwt.getClaim("userId");
        PagedResponse<ProductSummaryResponseDTO> response = productService.getFavorites(consumerId, page);
        return ResponseEntity.ok(response);
    }

    /**
     * Agregar un producto a la lista de favoritos
     */
    @PostMapping("/favorites/{productId}")
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    public ResponseEntity<Void> addToFavorites(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        Long consumerId = jwt.getClaim("userId");
        productService.addFavorite(consumerId, productId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Remover un producto a la lista de favoritos
     */
    @DeleteMapping("/favorites/{productId}")
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    public ResponseEntity<Void> removeFromFavorites(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        Long consumerId = jwt.getClaim("userId");
        productService.removeFavorite(consumerId, productId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Contar los productos favoritos del consumidor
     */

    @GetMapping("/favorites/count")
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    public ResponseEntity<Long> countFavorites(@AuthenticationPrincipal Jwt jwt) {
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(productService.countFavoriteProductsByConsumerId(consumerId));
    }

    /*
    * Marcar un producto para que aparezca al final de los juegos de cada commercial
    */

    @PatchMapping("{productId}/gameReward")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<Void> markProductAsReward (@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        Long commercialId = jwt.getClaim("userId");
        productService.markProductAsReward(commercialId, productId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Proxy de imagen privada (PENDING/REJECTED): hace streaming directo desde R2
     * sin redirigir al cliente, evitando problemas de CORS con R2.
     * Accesible por admins o por el comerciante dueño del producto.
     * Soporta JWT vía header Authorization o query param ?token= (para img tags).
     */
    @GetMapping("/{productId}/private-image")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_COMMERCIAL')")
    public void getPrivateProductImage(
            @PathVariable Long productId,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse response) throws IOException {

        boolean isAdmin = SecurityContextHolder.getContext()
                .getAuthentication()
                .getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            Long commercialId = jwt.getClaim("userId");
            productService.getByIdAndCommercialId(productId, commercialId);
        }

        productService.streamPrivateProductImage(productId, response);
    }
}