package com.verygana2.services;

import org.springframework.stereotype.Service;

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
    
}
