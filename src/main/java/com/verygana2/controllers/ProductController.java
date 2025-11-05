package com.verygana2.controllers;

import java.math.BigDecimal;
import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

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
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.generic.EntityCreatedResponse;
import com.verygana2.dtos.product.requests.CreateOrEditProductRequest;
import com.verygana2.dtos.product.responses.ProductResponse;
import com.verygana2.dtos.product.responses.ProductSummaryResponse;
import com.verygana2.services.interfaces.ProductService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('ROLE_SELLER')")
    public ResponseEntity<EntityCreatedResponse> createProduct(@RequestBody @Valid CreateOrEditProductRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.created(URI.create("/products")).body(productService.create(request, userId));
    }

    @DeleteMapping("/delete/{productId}")
    @PreAuthorize("hasAuthority('ROLE_SELLER')")
    public ResponseEntity<Void> deleteProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        Long userId = jwt.getClaim("userId");
        productService.delete(productId, userId);
        return ResponseEntity.noContent().build();

    }

    @DeleteMapping("/delete/admin/{productId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteProductForAdmin(@PathVariable Long productId) {
        productService.deleteForAdmin(productId);
        return ResponseEntity.noContent().build();

    }

    @PutMapping("/edit/{productId}")
    @PreAuthorize("hasAuthority('ROLE_SELLER')")
    public ResponseEntity<Void> editProduct(@RequestBody @Valid CreateOrEditProductRequest request,
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        Long userId = jwt.getClaim("userId");
        productService.edit(productId, userId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Page<ProductSummaryResponse>> loadProducts(
            @RequestParam(defaultValue = "0") Integer page) {
        Page<ProductSummaryResponse> response = productService.getAllProducts(page);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/filter")
    public ResponseEntity<Page<ProductSummaryResponse>> searchProducts(
            @RequestParam(required = false) String searchQuery,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) BigDecimal maxPrice, @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        Page<ProductSummaryResponse> response = productService.filterProducts(searchQuery, categoryId, minRating,
                maxPrice, page, sortBy, sortDirection);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> detailProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.detailProduct(productId));
    }

    @GetMapping("/{sellerId}")
    public ResponseEntity<Page<ProductSummaryResponse>> getSellerProducts(@PathVariable Long sellerId,
            @RequestParam(defaultValue = "0") Integer page) {
        return ResponseEntity.ok(productService.getSellerProducts(sellerId, page));
    }

    @GetMapping("/myProducts")
    @PreAuthorize("hasAuthority('ROLE_SELLER')")
    public ResponseEntity<Page<ProductSummaryResponse>> getMyProducts(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") Integer page) {
        Long sellerId = jwt.getClaim("userId");
        return ResponseEntity.ok(productService.getSellerProducts(sellerId, page));
    }

    @GetMapping("/favorites")
    @PreAuthorize("hasAuthority('ROLE_CONSUMER')")
    public ResponseEntity<Page<ProductSummaryResponse>> getFavorites(
            @RequestParam(defaultValue = "0") Integer page,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        Page<ProductSummaryResponse> response = productService.getFavorites(userId, page);
        return ResponseEntity.ok(response);
    }

    @PostMapping("{productId}/favorites")
    @PreAuthorize("hasAuthority('ROLE_CONSUMER')")
    public ResponseEntity<Void> addToFavorites(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId){
        Long userId = jwt.getClaim("userId");
        productService.addFavorite(userId, productId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("{productId}/favorites")
    @PreAuthorize("hasAuthority('ROLE_CONSUMER')")
    public ResponseEntity<Void> removeFromFavorites(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId){
        Long userId = jwt.getClaim("userId");
        productService.removeFavorite(userId, productId);
        return ResponseEntity.noContent().build();
    }

}

/*
 * FUTURE POSIBLE METHODS
 * 
 * @PatchMapping("/{productId}/stock")
 * 
 * @PreAuthorize("hasAuthority('ROLE_SELLER')")
 * public ResponseEntity<Void> updateStock(
 * 
 * @PathVariable Long productId,
 * 
 * @RequestParam Integer quantity,
 * 
 * @AuthenticationPrincipal Jwt jwt) {
 * Long userId = jwt.getClaim("userId");
 * productService.updateStock(productId, userId, quantity);
 * return ResponseEntity.ok().build();
 * }
 * 
 * @PatchMapping("/{productId}/toggle-availability")
 * 
 * @PreAuthorize("hasAuthority('ROLE_SELLER')")
 * public ResponseEntity<Void> toggleAvailability(
 * 
 * @PathVariable Long productId,
 * 
 * @AuthenticationPrincipal Jwt jwt) {
 * Long userId = jwt.getClaim("userId");
 * productService.toggleAvailability(productId, userId);
 * return ResponseEntity.ok().build();
 * }
 * 2. Productos del vendedor (SELLER)
 * java@GetMapping("/my-products")
 * 
 * @PreAuthorize("hasAuthority('ROLE_SELLER')")
 * public ResponseEntity<Page<ProductSummaryResponse>> getMyProducts(
 * 
 * @RequestParam(defaultValue = "0") Integer page,
 * 
 * @RequestParam(defaultValue = "10") Integer size,
 * 
 * @RequestParam(required = false) String status, // ACTIVE, INACTIVE,
 * OUT_OF_STOCK
 * 
 * @AuthenticationPrincipal Jwt jwt) {
 * Long userId = jwt.getClaim("userId");
 * Page<ProductSummaryResponse> response =
 * productService.getSellerProducts(userId, page, size, status);
 * return ResponseEntity.ok(response);
 * }
 * 3. Productos relacionados/recomendados (PÚBLICO)
 * java@GetMapping("/{productId}/related")
 * public ResponseEntity<List<ProductSummaryResponse>> getRelatedProducts(
 * 
 * @PathVariable Long productId,
 * 
 * @RequestParam(defaultValue = "6") Integer limit) {
 * List<ProductSummaryResponse> response =
 * productService.getRelatedProducts(productId, limit);
 * return ResponseEntity.ok(response);
 * }
 * 4. Productos por categoría (PÚBLICO)
 * java@GetMapping("/category/{categoryId}")
 * public ResponseEntity<Page<ProductSummaryResponse>> getProductsByCategory(
 * 
 * @PathVariable Long categoryId,
 * 
 * @RequestParam(defaultValue = "0") Integer page,
 * 
 * @RequestParam(defaultValue = "20") Integer size,
 * 
 * @RequestParam(defaultValue = "createdAt") String sortBy,
 * 
 * @RequestParam(defaultValue = "DESC") String sortDirection) {
 * Page<ProductSummaryResponse> response = productService.getProductsByCategory(
 * categoryId, page, size, sortBy, sortDirection);
 * return ResponseEntity.ok(response);
 * }
 * 5. Productos destacados/populares (PÚBLICO)
 * java@GetMapping("/featured")
 * public ResponseEntity<List<ProductSummaryResponse>> getFeaturedProducts(
 * 
 * @RequestParam(defaultValue = "10") Integer limit) {
 * List<ProductSummaryResponse> response =
 * productService.getFeaturedProducts(limit);
 * return ResponseEntity.ok(response);
 * }
 * 
 * @GetMapping("/best-sellers")
 * public ResponseEntity<List<ProductSummaryResponse>> getBestSellingProducts(
 * 
 * @RequestParam(defaultValue = "10") Integer limit) {
 * List<ProductSummaryResponse> response =
 * productService.getBestSellingProducts(limit);
 * return ResponseEntity.ok(response);
 * }
 * 
 * @GetMapping("/newest")
 * public ResponseEntity<List<ProductSummaryResponse>> getNewestProducts(
 * 
 * @RequestParam(defaultValue = "10") Integer limit) {
 * List<ProductSummaryResponse> response =
 * productService.getNewestProducts(limit);
 * return ResponseEntity.ok(response);
 * }
 * 6. Productos por vendedor (PÚBLICO)
 * java@GetMapping("/seller/{sellerId}")
 * public ResponseEntity<Page<ProductSummaryResponse>> getProductsBySeller(
 * 
 * @PathVariable Long sellerId,
 * 
 * @RequestParam(defaultValue = "0") Integer page,
 * 
 * @RequestParam(defaultValue = "20") Integer size) {
 * Page<ProductSummaryResponse> response =
 * productService.getProductsBySeller(sellerId, page, size);
 * return ResponseEntity.ok(response);
 * }
 * 7. Estadísticas del producto (SELLER/ADMIN)
 * java@GetMapping("/{productId}/stats")
 * 
 * @PreAuthorize("hasAnyAuthority('ROLE_SELLER', 'ROLE_ADMIN')")
 * public ResponseEntity<ProductStatsResponse> getProductStats(
 * 
 * @PathVariable Long productId,
 * 
 * @AuthenticationPrincipal Jwt jwt) {
 * Long userId = jwt.getClaim("userId");
 * ProductStatsResponse response = productService.getProductStats(productId,
 * userId);
 * return ResponseEntity.ok(response);
 * }
 * 8. Gestión administrativa (ADMIN)
 * java@GetMapping("/admin/pending-approval")
 * 
 * @PreAuthorize("hasAuthority('ROLE_ADMIN')")
 * public ResponseEntity<Page<ProductSummaryResponse>> getPendingProducts(
 * 
 * @RequestParam(defaultValue = "0") Integer page) {
 * Page<ProductSummaryResponse> response =
 * productService.getPendingProducts(page);
 * return ResponseEntity.ok(response);
 * }
 * 
 * @PatchMapping("/admin/{productId}/approve")
 * 
 * @PreAuthorize("hasAuthority('ROLE_ADMIN')")
 * public ResponseEntity<Void> approveProduct(@PathVariable Long productId) {
 * productService.approveProduct(productId);
 * return ResponseEntity.ok().build();
 * }
 * 
 * @PatchMapping("/admin/{productId}/reject")
 * 
 * @PreAuthorize("hasAuthority('ROLE_ADMIN')")
 * public ResponseEntity<Void> rejectProduct(
 * 
 * @PathVariable Long productId,
 * 
 * @RequestParam String reason) {
 * productService.rejectProduct(productId, reason);
 * return ResponseEntity.ok().build();
 * }
 * 9. Búsqueda mejorada (PÚBLICO)
 * java@GetMapping("/search/suggestions")
 * public ResponseEntity<List<String>> getSearchSuggestions(
 * 
 * @RequestParam String query,
 * 
 * @RequestParam(defaultValue = "5") Integer limit) {
 * List<String> suggestions = productService.getSearchSuggestions(query, limit);
 * return ResponseEntity.ok(suggestions);
 * }
 * 10. Favoritos/Wishlist (CONSUMER)
 * java@PostMapping("/{productId}/favorite")
 * 
 * @PreAuthorize("hasAuthority('ROLE_CONSUMER')")
 * public ResponseEntity<Void> addToFavorites(
 * 
 * @PathVariable Long productId,
 * 
 * @AuthenticationPrincipal Jwt jwt) {
 * Long userId = jwt.getClaim("userId");
 * productService.addToFavorites(productId, userId);
 * return ResponseEntity.ok().build();
 * }
 * 
 * @DeleteMapping("/{productId}/favorite")
 * 
 * @PreAuthorize("hasAuthority('ROLE_CONSUMER')")
 * public ResponseEntity<Void> removeFromFavorites(
 * 
 * @PathVariable Long productId,
 * 
 * @AuthenticationPrincipal Jwt jwt) {
 * Long userId = jwt.getClaim("userId");
 * productService.removeFromFavorites(productId, userId);
 * return ResponseEntity.ok().build();
 * }
 * 
 * @GetMapping("/favorites")
 * 
 * @PreAuthorize("hasAuthority('ROLE_CONSUMER')")
 * public ResponseEntity<Page<ProductSummaryResponse>> getFavorites(
 * 
 * @RequestParam(defaultValue = "0") Integer page,
 * 
 * @AuthenticationPrincipal Jwt jwt) {
 * Long userId = jwt.getClaim("userId");
 * Page<ProductSummaryResponse> response = productService.getFavorites(userId,
 * page);
 * return ResponseEntity.ok(response);
 * }
 */