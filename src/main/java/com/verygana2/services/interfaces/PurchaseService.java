package com.verygana2.services.interfaces;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponse;
import com.verygana2.dtos.purchase.requests.CreatePurchaseRequestDTO;
import com.verygana2.models.Transaction;
import com.verygana2.models.products.Purchase;

public interface PurchaseService {
    EntityCreatedResponse createPurchase(Long consumerId, CreatePurchaseRequestDTO request);
    PagedResponse<Purchase> getPurchases(Long consumerId, Pageable pageable);
    List<Transaction> getPurchaseTransactions (Long purchaseId);
    void cancelPurchase(Long purchaseId, Long userId, String reason);
    void cancelPurchaseItem(Long purchaseId, Long itemId, Long userId, String reason);
}
