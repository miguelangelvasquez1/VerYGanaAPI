package com.verygana2.services.interfaces;

import java.util.List;

import com.verygana2.models.products.PurchaseItem;

public interface PurchaseItemService {
    Long getTotalSalesbySeller (Long sellerId);
    List<PurchaseItem> getDeliveredItemsWithoutReview(Long consumerId);
    boolean canUserReviewPurchaseItem(Long purchaseItemId, Long consumerId);
    PurchaseItem getByIdAndConsumerId(Long purchaseItemId, Long consumerId);

}
