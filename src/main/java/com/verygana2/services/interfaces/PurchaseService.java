package com.verygana2.services.interfaces;

import java.util.List;

import com.verygana2.dtos.purchase.requests.CreatePurchaseRequestDTO;
import com.verygana2.models.Transaction;
import com.verygana2.models.products.Purchase;

public interface PurchaseService {
    Purchase createPurchase(Long consumerId, CreatePurchaseRequestDTO request);
    List<Transaction> getPurchaseTransactions (Long purchaseId);
    void cancelPurchase(Long purchaseId, Long userId, String reason);
    void cancelPurchaseItem(Long purchaseId, Long itemId, Long userId, String reason);
}
