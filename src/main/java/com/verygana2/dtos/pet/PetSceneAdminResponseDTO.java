package com.verygana2.dtos.pet;

import java.util.List;

public record PetSceneAdminResponseDTO(
        Long id,
        Integer sceneId,
        Boolean active,
        List<PetSceneObjectResponseDTO> objects
) {}