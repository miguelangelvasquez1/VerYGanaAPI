package com.verygana2.dtos.game.campaign;

import com.verygana2.dtos.FileUploadPermissionDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssetUploadPermissionDTO {
    
    private Long assetId;
    private FileUploadPermissionDTO permission;
}
