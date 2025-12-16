package com.verygana2.services.interfaces;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.purchase.requests.CreatePurchaseRequestDTO;
import com.verygana2.dtos.purchase.responses.PurchaseResponseDTO;
import com.verygana2.models.Transaction;
import com.verygana2.models.products.Purchase;

public interface PurchaseService {
    EntityCreatedResponseDTO createPurchase(Long consumerId, CreatePurchaseRequestDTO request);
    Purchase getPurchaseById (Long purchaseId);
    PagedResponse<PurchaseResponseDTO> getConsumerPurchases(Long consumerId, Pageable pageable);
    List<Transaction> getPurchaseTransactions (Long purchaseId);
    PurchaseResponseDTO getPurchaseResponseDTO (Long purchaseId, Long consumerId);
}
