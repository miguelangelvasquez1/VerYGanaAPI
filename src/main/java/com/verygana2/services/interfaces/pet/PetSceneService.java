package com.verygana2.services.interfaces.pet;


import com.verygana2.dtos.pet.PetSceneResponseDTO;
import java.util.List;

public interface PetSceneService {
    List<PetSceneResponseDTO> getAllScenes();
}