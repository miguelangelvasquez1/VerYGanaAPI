package com.verygana2.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadRequestDTO {
    
    @NotNull(message = "The file must have file name")
    private String originalFileName;
    @NotNull(message = "The file must have a valid content-type")
    private String contentType;
    @NotNull(message = "The file must contain a bytes size")
    private Long sizeBytes;
}