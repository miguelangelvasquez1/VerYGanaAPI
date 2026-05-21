package com.verygana2.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadRequestDTO {
    
    @NotBlank(message = "Original file name is required")
    private String originalFileName;
 
    @NotBlank(message = "Content type is required")
    private String contentType;
 
    @NotNull(message = "File size is required")
    @Positive(message = "File size must be positive")
    private Long sizeBytes;
 
    @Min(value = 5, message = "Image display duration must be at least 5 seconds")
    @Max(value = 60, message = "Image display duration cannot exceed 60 seconds")
    private Integer imageDurationSeconds;
}