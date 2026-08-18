package com.verygana2.services.interfaces.pet;


import com.verygana2.dtos.pet.PetCatalogItemRequestDTO;
import com.verygana2.dtos.pet.PetCatalogItemResponseDTO;
import java.util.List;

public interface PetCatalogService {
    List<PetCatalogItemResponseDTO> getAllCatalogItems();
    List<PetCatalogItemResponseDTO> getAllCatalogItemsAdmin();
    PetCatalogItemResponseDTO createCatalogItem(PetCatalogItemRequestDTO dto);

    /**
     * Igual que {@link #createCatalogItem(PetCatalogItemRequestDTO)} pero fijando la clave
     * del sprite en R2. El mapper ignora {@code spriteObjectKey} (el DTO expone la URL, no
     * la clave), así que esta es la única vía por API para que un ítem nazca con imagen.
     */
    PetCatalogItemResponseDTO createCatalogItem(PetCatalogItemRequestDTO dto, String spriteObjectKey);
    PetCatalogItemResponseDTO updateCatalogItem(Long id, PetCatalogItemRequestDTO dto);
    void deleteCatalogItem(Long id);
}