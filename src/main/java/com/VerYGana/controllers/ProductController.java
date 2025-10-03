package com.VerYGana.controllers;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;

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

import com.VerYGana.dtos2.generic.EntityCreatedResponse;
import com.VerYGana.dtos2.products.requests2.CreateOrEditProductRequest;
import com.VerYGana.dtos2.products.responses2.ProductSummaryResponse;
import com.VerYGana.services.interfaces.ProductService;

@RestController
@RequestMapping("/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('ROLE_SELLER')")
    public ResponseEntity<EntityCreatedResponse> createProduct(@RequestBody CreateOrEditProductRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId");
        productService.create(request, userId);
        EntityCreatedResponse response = new EntityCreatedResponse("The product has been created succesfully",
                Instant.now());
        return ResponseEntity.created(URI.create("/products")).body(response);
    }

    @DeleteMapping("/delete/{productId}")
    @PreAuthorize("hasAuthority('ROLE_SELLER')")
    public ResponseEntity<Void> deleteProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        Long userId = jwt.getClaim("userId");
        productService.delete(productId, userId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @PostMapping("/edit/{productId}")
    @PreAuthorize("hasAuthority('ROLE_SELLER')")
    public ResponseEntity<Void> editProduct(@RequestBody CreateOrEditProductRequest request,
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long productId) {
        Long userId = jwt.getClaim("userId");
        productService.edit(productId, userId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Page<ProductSummaryResponse>> searchProducts(@RequestParam String searchQuery,
            @RequestParam Long categoryId, @RequestParam Double minRating,
            @RequestParam BigDecimal maxPrice, @RequestParam Integer page,
            @RequestParam String sortBy, @RequestParam String sortDirection) {

        Page<ProductSummaryResponse> response = productService.searchProducts(searchQuery, categoryId, minRating,
                maxPrice, page, sortBy, sortDirection);
        return ResponseEntity.ok(response);
    }

}
