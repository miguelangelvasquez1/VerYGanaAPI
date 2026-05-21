package com.verygana2.dtos.pet;

public record PetSceneObjectResponseDTO(
        String id,
        String type,
        String url,
        Integer x,
        Integer y,
        Integer width,
        Integer height,
        Double scaleMultiplier
) {}