package com.verygana2.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadPermissionDTO { //Poner id de la entidad que contiene el archivo a subir?

    /** URL firmada para subir (PUT) */
    private String uploadUrl;

    /** Tiempo de expiración en segundos */
    private Long expiresInSeconds;
}