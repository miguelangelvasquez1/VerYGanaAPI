package com.verygana2.dtos.pqrs.responses;

import com.verygana2.dtos.FileUploadPermissionDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PqrsAssetUploadPermissionDTO {
    private Long assetId;
    private FileUploadPermissionDTO permission;
}
