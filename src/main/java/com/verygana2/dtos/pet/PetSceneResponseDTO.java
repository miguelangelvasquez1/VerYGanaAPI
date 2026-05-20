package com.verygana2.dtos.pet;


import java.util.List;

public record PetSceneResponseDTO(
        Integer sceneId,
        List<PetSceneObjectResponseDTO> objects
) {}