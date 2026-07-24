package com.verygana2.controllers.marketplace;


import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.product.requests.CreateProductReviewRequestDTO;
import com.verygana2.dtos.product.responses.ProductReviewResponseDTO;
import com.verygana2.services.interfaces.marketplace.ProductReviewService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/productsReviews")
@RequiredArgsConstructor
public class ProductReviewController {
    
    private final ProductReviewService productReviewService;

    @GetMapping("/commercial/avg")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<Double> getCommercialAvgRating (@AuthenticationPrincipal Jwt jwt){
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(productReviewService.getCommercialAvgRating(commercialId));
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    public ResponseEntity<EntityCreatedResponseDTO> createProductReview (@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateProductReviewRequestDTO request){
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(productReviewService.createProductReview(consumerId, request));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<PagedResponse<ProductReviewResponseDTO>> getProductReviewsByProductId (@PathVariable Long productId, @PageableDefault(size = 20, page = 0, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(productReviewService.getProductReviewList(productId, pageable));
    }

    @PatchMapping("/{reviewId}/hide")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> hideProductReview (@PathVariable Long reviewId){
        productReviewService.hideProductReview(reviewId);
        return ResponseEntity.noContent().build();
    }
}

