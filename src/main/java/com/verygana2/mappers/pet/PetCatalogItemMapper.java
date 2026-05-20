package com.verygana2.mappers.pet;



import com.verygana2.dtos.pet.PetCatalogItemRequestDTO;
import com.verygana2.dtos.pet.PetCatalogItemResponseDTO;
import com.verygana2.models.pets.PetCatalogItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PetCatalogItemMapper {

    @Mapping(target = "spriteUrl", ignore = true)
    PetCatalogItemResponseDTO toResponseDTO(PetCatalogItem item);

    @Mapping(target = "id", ignore = true)
    PetCatalogItem toEntity(PetCatalogItemRequestDTO dto);

    @Mapping(target = "id", ignore = true)
    void updateFromDto(PetCatalogItemRequestDTO dto, @MappingTarget PetCatalogItem item);
}