package com.verygana2.dtos.game.campaign;

import com.verygana2.dtos.FileUploadPermissionDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetUploadPermissionDTO {
    
    private Long assetId;
    private String temporalUrl; //URL para visualizar el archivo
    private String publicUrl; //URL final
    private FileUploadPermissionDTO permission;
}
