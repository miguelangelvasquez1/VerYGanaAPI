package com.verygana2.dtos.branding;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateBrandingRequestDTO {

    @NotNull(message = "Game ID is required")
    private Long gameId;

    @NotBlank(message = "Brand name is required")
    @Size(max = 200, message = "Brand name must not exceed 200 characters")
    private String brandName;

    @NotBlank(message = "Brand description is required")
    @Size(max = 1000, message = "Brand description must not exceed 1000 characters")
    private String brandDescription;

    @URL(message = "Target URL must be a valid URL (e.g. https://example.com)")
    @Size(max = 500, message = "Target URL must not exceed 500 characters")
    private String targetUrl;

    @NotNull(message = "Budget is required")
    @Min(value = 1, message = "Budget must be greater than 0")
    private Long budgetCents;
}
