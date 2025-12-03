package com.verygana2.services;

import java.util.List;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;

import com.verygana2.models.products.PurchaseItem;
import com.verygana2.repositories.PurchaseItemRepository;
import com.verygana2.services.interfaces.PurchaseItemService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PurchaseItemServiceImpl implements PurchaseItemService{

    private final PurchaseItemRepository purchaseItemRepository;

    @Override
    public Long getTotalSalesbySeller(Long sellerId) {
        if (sellerId == null) {
            throw new IllegalArgumentException("Seller id cannot be null");
        }

        if (sellerId <= 0) {
            throw new IllegalArgumentException("Seller id must be positive");
        }
        return purchaseItemRepository.countTotalSalesBySellerId(sellerId);
    }

    @Override
    public List<PurchaseItem> getDeliveredItemsWithoutReview(Long consumerId) {
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        return purchaseItemRepository.findDeliveredItemsWithoutReview(consumerId);
    }

    @Override
    public boolean canUserReviewPurchaseItem(Long purchaseItemId, Long consumerId) {
        if (purchaseItemId == null || purchaseItemId <= 0) {
            throw new IllegalArgumentException("purchaseItem id must be positive");
        }

        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        return purchaseItemRepository.canUserReviewPurchaseItem(purchaseItemId, consumerId);
    }

    @Override
    public PurchaseItem getByIdAndConsumerId(Long purchaseItemId, Long consumerId) {
        
        if (purchaseItemId == null || purchaseItemId <= 0) {
            throw new IllegalArgumentException("purchaseItem id must be positive");
        }

        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        return purchaseItemRepository.findByIdAndConsumerId(purchaseItemId, consumerId).orElseThrow(() -> new ObjectNotFoundException("Purchase item with id:" + purchaseItemId + " not found", PurchaseItem.class));
    }

    
    
}
