package com.verygana2.dtos.user.commercial.onboarding;

import com.verygana2.dtos.FileUploadPermissionDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadPermissionDTO {
    private Long documentId;
    private FileUploadPermissionDTO permission;
}
