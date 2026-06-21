package com.verygana2.dtos.branding;

import com.verygana2.dtos.FileUploadPermissionDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorporateResourceUploadPermissionDTO {

    private Long resourceId;
    private String temporalUrl;
    private FileUploadPermissionDTO permission;
}
