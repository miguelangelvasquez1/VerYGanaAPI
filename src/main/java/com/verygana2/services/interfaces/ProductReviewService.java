package com.verygana2.services.interfaces;


import java.util.List;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.product.requests.CreateProductReviewRequestDTO;
import com.verygana2.dtos.product.responses.ProductReviewResponseDTO;
import com.verygana2.dtos.purchase.responses.PurchaseItemToReviewResponseDTO;

public interface ProductReviewService {
    EntityCreatedResponseDTO createProductReview (Long consumerId, CreateProductReviewRequestDTO request);
    Double getProductAvgRating (Long productId);
    Double getSellerAvgRating (Long sellerId);
    PagedResponse<ProductReviewResponseDTO> getProductReviewList (Long productId, Integer pageIndex);
    List<PurchaseItemToReviewResponseDTO> getPurchaseItemsToReview(Long consumerId);
}
