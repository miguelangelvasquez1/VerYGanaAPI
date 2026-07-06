package com.verygana2.dtos.pet;

import java.util.List;

public record PetSceneRequestDTO(
        Integer sceneId,
        Boolean active,
        List<PetSceneObjectRequestDTO> objects
) {}