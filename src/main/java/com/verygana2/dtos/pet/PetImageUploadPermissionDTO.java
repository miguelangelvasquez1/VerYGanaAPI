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
        Long expiresInSeconds,

        /**
         * URL pública donde quedará el archivo una vez subido.
         *
         * Se devuelve desde ya para que el cliente pueda mostrarlo sin haber
         * guardado todavía: hasta ahora la url solo llegaba al releer la entidad,
         * así que el diseñador subía un objeto de escena y no veía nada hasta
         * guardar y recargar. Es la misma que devolverán después los listados.
         */
        String publicUrl
) {}
