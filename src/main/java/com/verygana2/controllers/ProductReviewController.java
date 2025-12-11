package com.verygana2.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.product.requests.CreateProductReviewRequestDTO;
import com.verygana2.dtos.product.responses.ProductReviewResponseDTO;
import com.verygana2.dtos.purchase.responses.PurchaseItemToReviewResponseDTO;
import com.verygana2.services.interfaces.ProductReviewService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/productReviews")
@RequiredArgsConstructor
public class ProductReviewController {
    
    private ProductReviewService productReviewService;

    @GetMapping("/{productId}/avg")
    @PreAuthorize("hasRole('ROLE_SELLER')")
    public ResponseEntity<Double> getProductAvgRating (@PathVariable Long productId){
        return ResponseEntity.ok(productReviewService.getProductAvgRating(productId));
    }

    @GetMapping("/{sellerId}/avg")
    @PreAuthorize("hasRole('ROLE_SELLER')")
    public ResponseEntity<Double> getSellerAvgRating (@AuthenticationPrincipal Jwt jwt){
        Long sellerId = jwt.getClaim("userId");
        return ResponseEntity.ok(productReviewService.getSellerAvgRating(sellerId));
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    public ResponseEntity<EntityCreatedResponseDTO> createProductReview (@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateProductReviewRequestDTO request){
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(productReviewService.createProductReview(consumerId, request));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<PagedResponse<ProductReviewResponseDTO>> getProductReviewList (@PathVariable Long productId, @RequestParam Integer pageIndex) {
        return ResponseEntity.ok(productReviewService.getProductReviewList(productId, pageIndex));
    }

    /**
     * Obtener items que el usuario puede revisar
     * GET /reviews/pending
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    public ResponseEntity<List<PurchaseItemToReviewResponseDTO>> getPendingReviews(
            @AuthenticationPrincipal Jwt jwt) {
        
        Long consumerId = jwt.getClaim("userId");
        List<PurchaseItemToReviewResponseDTO> items = productReviewService.getPurchaseItemsToReview(consumerId);
        return ResponseEntity.ok(items);
    }


}

