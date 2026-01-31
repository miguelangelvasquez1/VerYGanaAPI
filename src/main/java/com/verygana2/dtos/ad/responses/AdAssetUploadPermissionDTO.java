package com.verygana2.dtos.ad.responses;

import com.verygana2.dtos.FileUploadPermissionDTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdAssetUploadPermissionDTO {
    
    @NotNull
    private Long assetId;
    
    @NotNull
    @Valid
    private FileUploadPermissionDTO permission;
}