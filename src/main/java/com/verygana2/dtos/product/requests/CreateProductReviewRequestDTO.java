package com.verygana2.dtos.product.requests;



import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateProductReviewRequestDTO {
    @NotNull(message = "Purchase item id is required")
    private Long purchaseItemId;
    @NotBlank(message = "Comment cannot be empty")
    @Size(max = 1000, message = "Comment cannot exceed 1000 characters")
    private String comment;
    @Max(value = 5, message = "The rating maximum is 5")
    @Min(value = 1, message = "The rating minimum is 1")
    @NotNull (message = "Rating is required")
    private Integer rating;
}
