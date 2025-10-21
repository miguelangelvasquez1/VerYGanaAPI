package com.verygana2.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryRequestDTO {

    @NotBlank(message = "The category name is required")
    @Size(max = 50, message = "The category name cannot exceed 50 characters")
    private String name;
}