package com.verygana2.services.pet;




import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.pet.PetCatalogItemRequestDTO;
import com.verygana2.dtos.pet.PetCatalogItemResponseDTO;

import com.verygana2.mappers.pet.PetCatalogItemMapper;
import com.verygana2.models.pets.PetCatalogItem;
import com.verygana2.repositories.pet.PetCatalogItemRepository;
import com.verygana2.services.interfaces.pet.PetCatalogService;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PetCatalogServiceImpl implements PetCatalogService {

    @Value("${cloudflare.r2.pets-cdn-domain:}")
    private String petsCdnDomain;

    @Value("${cloudflare.r2.pets-bucket-name:verygana-pets}")
    private String petsBucketName;

    private final PetCatalogItemRepository catalogRepository;
    private final PetCatalogItemMapper catalogMapper;
    private final ObjectMapper objectMapper;

    private String buildPublicUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return "";
        if (petsCdnDomain != null && !petsCdnDomain.isBlank()) {
            return String.format("https://%s/%s", petsCdnDomain, objectKey);
        }
        return String.format("https://%s.r2.dev/%s", petsBucketName, objectKey);
    }

    @Override
    public List<PetCatalogItemResponseDTO> getAllCatalogItems() {
        return catalogRepository.findAllByActiveTrue()
                .stream()
                .map(item -> {
                    PetCatalogItemResponseDTO dto = catalogMapper.toResponseDTO(item);
                    String url = buildPublicUrl(item.getSpriteObjectKey());
                    return new PetCatalogItemResponseDTO(
                            dto.id(), dto.externalId(), dto.name(), dto.description(),
                            dto.isMedicine(), dto.isDrink(), dto.curesAllParts(),
                            dto.price(), url, dto.expWhenEating(),
                            dto.healthDelta(), dto.energyDelta(), dto.hungerDelta(),
                            dto.thirstDelta(), dto.hygieneDelta(), dto.humorDelta(),
                            dto.bodyFatDelta(), dto.active()
                    );
                })
                .toList();
    }

    @Override
    public PetCatalogItemResponseDTO createCatalogItem(PetCatalogItemRequestDTO dto) {
        return catalogMapper.toResponseDTO(
                catalogRepository.save(catalogMapper.toEntity(dto))
        );
    }

    @Override
    public PetCatalogItemResponseDTO updateCatalogItem(Long id, PetCatalogItemRequestDTO dto) {
        PetCatalogItem item = catalogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Item not found"));
        catalogMapper.updateFromDto(dto, item);
        return catalogMapper.toResponseDTO(catalogRepository.save(item));
    }

    @Override
    public void deleteCatalogItem(Long id) {
        PetCatalogItem item = catalogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Item not found"));
        item.setActive(false);
        catalogRepository.save(item);
    }


}
