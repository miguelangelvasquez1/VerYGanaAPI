package com.VerYGana.dtos.products.Requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateProductCategoryRequest {
    @NotBlank(message = "Product category name cannot be empty")
    @Size(max = 100, message = "The product category name cannot exceed 100 characters")
    private String name;

    @NotBlank(message = "Product category description cannot be empty")
    @Size(max = 200, message = "The product category description cannot exceed 200 characters")
    private String description;
    
    private String imageUrl;
   
}
