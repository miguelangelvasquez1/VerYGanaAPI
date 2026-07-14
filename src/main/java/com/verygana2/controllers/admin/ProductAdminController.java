package com.verygana2.controllers.admin;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.AssetUploadPermissionDTO;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.product.requests.ConfirmProductCategoryCreationRequestDTO;
import com.verygana2.dtos.product.responses.ProductCategoryResponseDTO;
import com.verygana2.dtos.product.responses.ProductResponseDTO;
import com.verygana2.dtos.product.responses.ProductSummaryResponseDTO;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.services.interfaces.marketplace.ProductCategoryService;
import com.verygana2.services.interfaces.marketplace.ProductService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class ProductAdminController {

    private final ProductCategoryService productCategoryService;
    private final ProductService productService;

    /**
     * Preparar la creacion de una categoria de producto
     */
    @PostMapping("/productCategories/prepare")
    public ResponseEntity<AssetUploadPermissionDTO> prepareProductCategoryCreation(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FileUploadRequestDTO productCategoryImageMetaData) {
        Long adminId = jwt.getClaim("userId");
        return ResponseEntity.ok(productCategoryService.prepareProductCategoryCreation(adminId, productCategoryImageMetaData));
    }

    /*
     * Crear categoria de producto (Luego de confirmar subida a R2)
     */
    @PostMapping("/productCategories/confirm")
    public ResponseEntity<EntityCreatedResponseDTO> confirmProductCategoryCreation(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConfirmProductCategoryCreationRequestDTO request) {
        Long adminId = jwt.getClaim("userId");
        return ResponseEntity.ok(productCategoryService.confirmProductCategoryCreation(adminId, request));
    }

    @DeleteMapping("/productCategories/{productCategoryId}")
    public ResponseEntity<Void> deleteProductCategory(@PathVariable Long productCategoryId){
        productCategoryService.delete(productCategoryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/productCategories/{productCategoryId}")
    public ResponseEntity<Void> recoverProductCategory(@PathVariable Long productCategoryId){
        productCategoryService.recover(productCategoryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/productCategories/inactives")
    public ResponseEntity<List<ProductCategoryResponseDTO>> getInactiveProductCategories () {
        return ResponseEntity.ok(productCategoryService.getInactiveProductCategories());
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ProductSummaryResponseDTO>> getAllProductsForAdmin (@RequestParam(required = false) ProductStatus status, @RequestParam(required = false) String search, @PageableDefault(page = 0, size = 5) Pageable pageable){
        return ResponseEntity.ok(productService.getAllProductsForAdmin(status, search, pageable));
    }

    @PostMapping("/{productId}/approve")
    public ResponseEntity<ProductResponseDTO> approveProduct (@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId){
        Long adminId = jwt.getClaim("userId");
        return ResponseEntity.ok(productService.approveProductForAdmin(adminId, productId));
    }

    @PostMapping("/{productId}/reject")
    public ResponseEntity<ProductResponseDTO> rejectProduct (@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId, @RequestParam("reason") String reason){
        Long adminId = jwt.getClaim("userId");
        return ResponseEntity.ok(productService.rejectProductForAdmin(adminId, productId, reason));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct (@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId, @RequestParam("reason") String reason){
        Long adminId = jwt.getClaim("userId");
        productService.deleteProductForAdmin(adminId, productId, reason);
        return ResponseEntity.noContent().build();
    }

}
