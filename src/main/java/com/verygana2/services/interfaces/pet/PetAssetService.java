package com.verygana2.services.interfaces.pet;

import com.verygana2.dtos.pet.PetAssetUploadRequestDTO;
import com.verygana2.dtos.pet.PetImageUploadPermissionDTO;

public interface PetAssetService {

    /**
     * Da permiso al diseñador para subir un asset al bucket de mascotas.
     *
     * @param designerUserId userId del diseñador (va en la clave, para trazabilidad)
     */
    PetImageUploadPermissionDTO prepareUpload(Long designerUserId, PetAssetUploadRequestDTO dto);
}
