package com.verygana2.services.interfaces;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.product.responses.FeaturedProductResponseDTO;
import com.verygana2.models.products.PurchaseItem;

public interface PurchaseItemService {
    Long getTotalSalesbyCommercial (Long commercialId);
    List<PurchaseItem> getDeliveredItemsWithoutReview(Long consumerId);
    boolean canUserReviewPurchaseItem(Long purchaseItemId, Long consumerId);
    PurchaseItem getByIdAndConsumerId(Long purchaseItemId, Long consumerId);
    BigDecimal getTotalCommercialSalesAmountByMonth(Long commercialId, Integer year, Integer month);
    Integer getTotalCommercialSalesByMonth (Long commercialId, Integer year, Integer month);
    BigDecimal getTotalPlatformComissionsByMonth(Long commercialId, Integer year, Integer month);
    PagedResponse<FeaturedProductResponseDTO> getTopSellingProductsPage(Long commercialId, Pageable pageable);
}
