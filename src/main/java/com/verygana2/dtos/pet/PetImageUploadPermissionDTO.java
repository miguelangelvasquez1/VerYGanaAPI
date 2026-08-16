package com.verygana2.dtos.pet;

/**
 * Permiso de subida devuelto al comercial.
 *
 * El cliente hace {@code PUT uploadUrl} con el archivo y el mismo Content-Type que
 * declaró, y después manda {@code objectKey} en el campo {@code imageObjectKey} al
 * crear la solicitud. La clave solo es válida para el comercial que la pidió.
 */
public record PetImageUploadPermissionDTO(

        /** Clave a enviar como imageObjectKey al crear la solicitud. */
        String objectKey,

        /** URL pre-firmada (PUT) contra el bucket de mascotas. */
        String uploadUrl,

        /** Segundos que la URL sigue siendo válida. */
        Long expiresInSeconds
) {}
