package com.verygana2.dtos.pet;

public record PetSceneObjectRequestDTO(
        String objectId,
        String type,
        String objectKey,
        Integer x,
        Integer y,
        Integer width,
        Integer height,
        Double scaleMultiplier
) {}