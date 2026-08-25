package com.verygana2.services.interfaces.pet;


import com.verygana2.dtos.pet.PetSceneResponseDTO;
import java.util.List;

import com.verygana2.dtos.pet.PetSceneAdminResponseDTO;
import com.verygana2.dtos.pet.PetSceneCanvasDTO;
import com.verygana2.dtos.pet.PetSceneRequestDTO;

public interface PetSceneService {
    List<PetSceneResponseDTO> getAllScenes();

    /**
     * Escenas para el modo preview del diseñador: incluye las que tienen
     * {@code active = false}, que son las que todavía no ve ningún consumidor.
     *
     * @param sceneId escena concreta a previsualizar; si es null devuelve todas
     *                (publicadas y borradores).
     */
    List<PetSceneResponseDTO> getScenesForPreview(Integer sceneId);

    List<PetSceneAdminResponseDTO> getAllScenesAdmin();
    PetSceneAdminResponseDTO createScene(PetSceneRequestDTO dto);
    PetSceneAdminResponseDTO updateScene(Long id, PetSceneRequestDTO dto);
    void deleteScene(Long id);

    /**
     * Área de juego contra la que el editor dibuja los objetos de una escena.
     * Es configuración, no datos: describe el build, no una escena concreta.
     */
    PetSceneCanvasDTO getSceneCanvas();
}