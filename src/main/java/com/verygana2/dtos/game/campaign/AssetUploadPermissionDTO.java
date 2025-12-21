package com.verygana2.dtos.game.campaign;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssetUploadPermissionDTO {

    /** URL firmada para subir (PUT) */
    private String uploadUrl;

    /** URL pública CDN definitiva */
    private String publicUrl;

    /** Tiempo de expiración en segundos */
    private Long expiresInSeconds;
}