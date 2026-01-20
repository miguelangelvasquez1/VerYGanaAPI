package com.verygana2.services.interfaces;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.product.responses.FeaturedProductResponseDTO;
import com.verygana2.models.products.PurchaseItem;

public interface PurchaseItemService {
    Long getTotalSalesbySeller (Long sellerId);
    List<PurchaseItem> getDeliveredItemsWithoutReview(Long consumerId);
    boolean canUserReviewPurchaseItem(Long purchaseItemId, Long consumerId);
    PurchaseItem getByIdAndConsumerId(Long purchaseItemId, Long consumerId);
    BigDecimal getTotalSellerSalesAmountByMonth(Long sellerId, Integer year, Integer month);
    Integer getTotalSellerSalesByMonth (Long sellerId, Integer year, Integer month);
    BigDecimal getTotalPlatformComissionsByMonth(Long sellerId, Integer year, Integer month);
    PagedResponse<FeaturedProductResponseDTO> getTopSellingProductsPage(Long sellerId, Pageable pageable);
}
