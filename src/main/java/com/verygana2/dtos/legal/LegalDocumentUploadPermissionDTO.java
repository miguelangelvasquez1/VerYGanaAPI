package com.verygana2.dtos.legal;

import com.verygana2.dtos.FileUploadPermissionDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LegalDocumentUploadPermissionDTO {
    private Long documentId;
    private FileUploadPermissionDTO permission;
}
