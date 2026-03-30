package com.verygana2.dtos.impactStory;

import com.verygana2.dtos.FileUploadPermissionDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Respuesta del PASO 1 del flujo de media.
 * El frontend usa el mediaAssetId para referenciar el archivo al crear la historia,
 * y la permission.uploadUrl para subir el archivo directamente a R2.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepareMediaUploadResponseDTO {

    /** ID del StoryMediaAsset creado en estado PENDING */
    private String mediaAssetId;

    /** Pre-signed URL y URL pública para el archivo */
    private FileUploadPermissionDTO permission;
}