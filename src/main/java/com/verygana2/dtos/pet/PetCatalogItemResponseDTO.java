package com.verygana2.dtos.pet;


public record PetCatalogItemResponseDTO(
        Long id,
        Integer externalId,
        String name,
        String description,
        Boolean isMedicine,
        Boolean isDrink,
        Boolean curesAllParts,
        Integer price,
        String spriteUrl,
        Integer expWhenEating,
        Integer healthDelta,
        Integer energyDelta,
        Integer hungerDelta,
        Integer thirstDelta,
        Integer hygieneDelta,
        Integer humorDelta,
        Integer bodyFatDelta,
        Boolean active
) {}
