package com.verygana2.dtos.generic;

import com.verygana2.dtos.FileUploadPermissionDTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AssetUploadPermissionDTO {

    @NotNull
    private Long assetId;

    // Pre-signed URL para subir la imagen del objeto
    @NotNull
    @Valid
    private FileUploadPermissionDTO imagePermission;
}
