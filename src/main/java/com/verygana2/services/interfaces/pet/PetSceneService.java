package com.verygana2.services.interfaces.pet;


import com.verygana2.dtos.pet.PetSceneResponseDTO;
import java.util.List;

import com.verygana2.dtos.pet.PetSceneAdminResponseDTO;
import com.verygana2.dtos.pet.PetSceneRequestDTO;

public interface PetSceneService {
    List<PetSceneResponseDTO> getAllScenes();
    List<PetSceneAdminResponseDTO> getAllScenesAdmin();
    PetSceneAdminResponseDTO createScene(PetSceneRequestDTO dto);
    PetSceneAdminResponseDTO updateScene(Long id, PetSceneRequestDTO dto);
    void deleteScene(Long id);
}