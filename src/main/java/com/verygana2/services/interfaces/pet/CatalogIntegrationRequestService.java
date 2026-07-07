package com.verygana2.services.interfaces.pet;

import com.verygana2.dtos.pet.CatalogIntegrationRequestDTO;
import com.verygana2.dtos.pet.CatalogIntegrationResponseDTO;
import com.verygana2.dtos.pet.PetCatalogItemRequestDTO;
import com.verygana2.models.enums.CatalogRequestStatus;

import java.util.List;

public interface CatalogIntegrationRequestService {

    // Commercial
    CatalogIntegrationResponseDTO submit(Long userId, CatalogIntegrationRequestDTO dto);
    List<CatalogIntegrationResponseDTO> getMyRequests(Long userId);

    // Game designer
    List<CatalogIntegrationResponseDTO> getAllRequests();
    List<CatalogIntegrationResponseDTO> getRequestsByStatus(CatalogRequestStatus status);
    CatalogIntegrationResponseDTO markInReview(Long requestId);
    CatalogIntegrationResponseDTO approve(Long requestId, PetCatalogItemRequestDTO catalogItemDto);
    CatalogIntegrationResponseDTO reject(Long requestId, String reason);
}