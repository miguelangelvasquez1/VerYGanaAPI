package com.verygana2.services.interfaces.marketplace;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.product.requests.CreateProductReviewRequestDTO;
import com.verygana2.dtos.product.responses.ProductReviewResponseDTO;


public interface ProductReviewService {
    EntityCreatedResponseDTO createProductReview (Long consumerId, CreateProductReviewRequestDTO request);
    Double getProductAvgRating (Long productId);
    Double getCommercialAvgRating (Long commercialId);
    Integer getCommercialReviewCount (Long commercialId);
    PagedResponse<ProductReviewResponseDTO> getProductReviewList (Long productId, Pageable pageable);
    boolean canBeReviewed (Long productId, Long consumerId);
}
