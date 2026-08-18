package com.verygana2.services.pet;

import com.verygana2.dtos.pet.PetCatalogItemRequestDTO;
import com.verygana2.dtos.pet.PetCatalogItemResponseDTO;
import com.verygana2.mappers.pet.PetCatalogItemMapper;
import com.verygana2.models.pets.PetCatalogItem;
import com.verygana2.repositories.pet.PetCatalogItemRepository;
import com.verygana2.services.interfaces.pet.PetCatalogService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    private String buildPublicUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return "";
        if (petsCdnDomain != null && !petsCdnDomain.isBlank()) {
            return String.format("https://%s/%s", petsCdnDomain, objectKey);
        }
        return String.format("https://%s.r2.dev/%s", petsBucketName, objectKey);
    }

    private PetCatalogItemResponseDTO toResponseWithUrl(PetCatalogItem item) {
        return toResponseWithUrl(item, item.getId());
    }

    /**
     * @param publicId qué va en el campo {@code id} de la respuesta. Para el panel
     *                 de diseñador es la PK (la necesita para editar y borrar);
     *                 para el juego es el externalId (ver {@link #getAllCatalogItems()}).
     */
    private PetCatalogItemResponseDTO toResponseWithUrl(PetCatalogItem item, Long publicId) {
        PetCatalogItemResponseDTO dto = catalogMapper.toResponseDTO(item);
        String url = buildPublicUrl(item.getSpriteObjectKey());
        return new PetCatalogItemResponseDTO(
                publicId, dto.externalId(), dto.name(), dto.description(),
                dto.isMedicine(), dto.isDrink(), dto.curesAllParts(),
                dto.price(), url, dto.expWhenEating(),
                dto.healthDelta(), dto.energyDelta(), dto.hungerDelta(),
                dto.thirstDelta(), dto.hygieneDelta(), dto.humorDelta(),
                dto.bodyFatDelta(), dto.active()
        );
    }

    /**
     * Catálogo que consume el juego.
     *
     * El {@code id} que se expone acá es el {@code externalId}, no la PK: el juego
     * lee ese campo (así está su contrato) y lo devuelve al comprar, así que es el
     * identificador con el que /spend resuelve el precio. Usar el externalId en su
     * lugar tiene dos ventajas: no filtramos claves internas, y el número queda bajo
     * nuestro control — las PK cambian si se recrea la base, y además chocan con los
     * ids del catálogo horneado en el build.
     *
     * Los ítems sin externalId se omiten: irían con id nulo y el juego no podría
     * comprarlos.
     */
    @Override
    public List<PetCatalogItemResponseDTO> getAllCatalogItems() {
        return catalogRepository.findAllByActiveTrue()
                .stream()
                .filter(item -> {
                    if (item.getExternalId() != null) return true;
                    log.warn("Ítem de catálogo id={} sin externalId: no se expone al juego", item.getId());
                    return false;
                })
                .map(item -> toResponseWithUrl(item, item.getExternalId().longValue()))
                .toList();
    }

    @Override
    public List<PetCatalogItemResponseDTO> getAllCatalogItemsAdmin() {
        return catalogRepository.findAll()
                .stream()
                .map(this::toResponseWithUrl)
                .toList();
    }

    /**
     * Toma el sprite del propio DTO. Antes pasaba {@code null} fijo, así que el CRUD del
     * diseñador —la única vía para crear un ítem a mano— no podía darle imagen a nada:
     * subías el asset, el ítem se guardaba con spriteObjectKey en null y salía en blanco.
     */
    @Override
    public PetCatalogItemResponseDTO createCatalogItem(PetCatalogItemRequestDTO dto) {
        return createCatalogItem(dto, dto.spriteObjectKey());
    }

    @Override
    public PetCatalogItemResponseDTO createCatalogItem(PetCatalogItemRequestDTO dto, String spriteObjectKey) {
        PetCatalogItem item = catalogMapper.toEntity(dto);
        if (spriteObjectKey != null && !spriteObjectKey.isBlank()) {
            item.setSpriteObjectKey(spriteObjectKey);
        }
        // El DTO trae active como Boolean: si el diseñador lo omite, MapStruct pisa el
        // default de la entidad con null y findAllByActiveTrue() lo dejaría invisible.
        if (item.getActive() == null) {
            item.setActive(true);
        }
        return toResponseWithUrl(catalogRepository.save(item));
    }

    @Override
    public PetCatalogItemResponseDTO updateCatalogItem(Long id, PetCatalogItemRequestDTO dto) {
        PetCatalogItem item = catalogRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Catalog item not found: " + id));
        catalogMapper.updateFromDto(dto, item);

        // Solo si viene con valor: el mapper ignora spriteObjectKey a propósito, y una
        // edición que no toca la imagen (cambiar el precio, por ejemplo) llega con el
        // campo vacío. Pisarlo con null borraría el sprite del ítem sin querer.
        if (dto.spriteObjectKey() != null && !dto.spriteObjectKey().isBlank()) {
            item.setSpriteObjectKey(dto.spriteObjectKey());
        }

        return toResponseWithUrl(catalogRepository.save(item));
    }

    @Override
    public void deleteCatalogItem(Long id) {
        PetCatalogItem item = catalogRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Catalog item not found: " + id));
        item.setActive(false);
        catalogRepository.save(item);
    }
}