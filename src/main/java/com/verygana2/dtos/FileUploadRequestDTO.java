package com.verygana2.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadRequestDTO {
    
    private String originalFileName;
    private String contentType;
    private Long sizeBytes;
}
