package com.verygana2.dtos.purchase.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelPurchaseOrProductRequestDTO {
    @NotBlank(message = "The reason is required")
    @Pattern(regexp = "^[a-zA-Z0-9\\s.,-]*$", message = "Only letters, numbers, and basic punctuation are allowed")
    @Size(max = 300)
    private String reason;
}
