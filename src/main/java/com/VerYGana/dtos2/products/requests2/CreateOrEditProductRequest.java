package com.VerYGana.dtos2.products.requests2;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CreateOrEditProductRequest {
    @NotBlank(message = "Product name cannot be empty")
    @Size(max = 150, message = "Product name cannot exceed 150 characters")
    private String name;
    @NotBlank(message = "Product description cannot be empty")
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    @NotNull(message = "Category id cannot be null")
    @Positive(message = "Category id must be a positive number")
    private Long categoryId;
    List<String> imagesUrls;
    @NotNull(message = "Stock cannot be null")
    @Positive(message = "Stock must be a positive number")
    private Integer stock;
    @NotNull(message = "Price cannot be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
    private BigDecimal price;
}
