package com.verygana2.services.interfaces;

import java.util.List;

import com.verygana2.dtos.generic.EntityCreatedResponse;
import com.verygana2.dtos.purchase.requests.CreatePurchaseRequestDTO;
import com.verygana2.models.Transaction;

public interface PurchaseService {
    EntityCreatedResponse createPurchase(Long consumerId, CreatePurchaseRequestDTO request);
    List<Transaction> getPurchaseTransactions (Long purchaseId);
    void cancelPurchase(Long purchaseId, Long userId, String reason);
    void cancelPurchaseItem(Long purchaseId, Long itemId, Long userId, String reason);
}
