package com.verygana2.dtos.pet;

import com.verygana2.models.enums.PetAssetKind;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Petición del diseñador para subir un asset al bucket de mascotas.
 *
 * A diferencia de la del comercial (que siempre es la foto de un producto), acá el
 * destino cambia qué formatos se aceptan: un sprite de catálogo tiene que ser imagen,
 * mientras que un objeto de escena puede ser un video.
 */
public record PetAssetUploadRequestDTO(

        @NotNull(message = "El destino del asset es requerido")
        PetAssetKind kind,

        @NotBlank(message = "El content-type es requerido")
        @Size(max = 100)
        String contentType,

        @NotBlank(message = "El nombre del archivo es requerido")
        @Size(max = 255)
        String originalFileName,

        @NotNull(message = "El tamaño del archivo es requerido")
        @Min(value = 1, message = "El archivo no puede estar vacío")
        Long sizeBytes
) {}
