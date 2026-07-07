package com.verygana2.services.interfaces.pet;


import com.verygana2.dtos.pet.PetCatalogItemRequestDTO;
import com.verygana2.dtos.pet.PetCatalogItemResponseDTO;
import java.util.List;

public interface PetCatalogService {
    List<PetCatalogItemResponseDTO> getAllCatalogItems();
    List<PetCatalogItemResponseDTO> getAllCatalogItemsAdmin();
    PetCatalogItemResponseDTO createCatalogItem(PetCatalogItemRequestDTO dto);
    PetCatalogItemResponseDTO updateCatalogItem(Long id, PetCatalogItemRequestDTO dto);
    void deleteCatalogItem(Long id);
}