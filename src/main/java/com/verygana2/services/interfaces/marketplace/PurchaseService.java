package com.verygana2.services.interfaces.marketplace;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.purchase.requests.CreatePurchaseRequestDTO;
import com.verygana2.dtos.purchase.responses.InitiatePurchaseResponseDTO;
import com.verygana2.dtos.purchase.responses.PurchaseResponseDTO;
import com.verygana2.models.marketplace.Purchase;

public interface PurchaseService {
    InitiatePurchaseResponseDTO createPurchase(Long consumerId, CreatePurchaseRequestDTO request);
    Purchase getPurchaseById(Long purchaseId);
    Purchase getByIdAndConsumerId(Long purchaseId, Long consumerId);
    PagedResponse<PurchaseResponseDTO> getConsumerPurchases(Long consumerId, Pageable pageable);
    PurchaseResponseDTO getPurchaseResponseDTO(Long purchaseId, Long consumerId);
}
