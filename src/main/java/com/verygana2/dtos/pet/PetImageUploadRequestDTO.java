package com.verygana2.dtos.pet;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Petición para obtener permiso de subida de la imagen del producto que el comercial
 * quiere integrar al catálogo de mascotas.
 */
public record PetImageUploadRequestDTO(

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
