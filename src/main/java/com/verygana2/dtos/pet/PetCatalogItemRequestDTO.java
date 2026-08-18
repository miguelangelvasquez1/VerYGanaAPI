package com.verygana2.dtos.pet;


public record PetCatalogItemRequestDTO(
        Integer externalId,
        String name,
        String description,
        Boolean isMedicine,
        Boolean isDrink,
        Boolean curesAllParts,
        Integer price,
        // Clave del asset en R2, la que devuelve POST /game-designer/pet/assets.
        // Antes este campo era `spriteUrl`: el front mandaba `spriteObjectKey`, Jackson
        // no lo reconocía, lo descartaba en silencio y el ítem nacía sin imagen.
        String spriteObjectKey,
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
