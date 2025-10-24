package com.verygana2.dtos.product.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateProductCategoryRequest {
    @NotBlank(message = "Product category name cannot be empty")
    @Size(max = 100, message = "The product category name cannot exceed 100 characters")
    private String name;

    @NotBlank(message = "Image url cannot be empty")
    private String imageUrl;
   
}
