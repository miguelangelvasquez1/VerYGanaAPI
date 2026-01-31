package com.verygana2.dtos.ad.requests;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAdRequestDTO {
    
    @NotNull(message = "Asset ID is required")
    private Long assetId;
    
    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 100, message = "Title must be between 5 and 100 characters")
    private String title;
    
    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 1000, message = "Description must be between 10 and 1000 characters")
    private String description;
    
    @NotNull(message = "Reward per like is required")
    @DecimalMin(value = "0.01", message = "Reward per like must be at least 0.01")
    @DecimalMax(value = "100.00", message = "Reward per like must not exceed 100.00")
    private BigDecimal rewardPerLike;
    
    @NotNull(message = "Max likes is required")
    @Min(value = 1, message = "Max likes must be at least 1")
    @Max(value = 10000, message = "Max likes must not exceed 10,000")
    private Integer maxLikes;
    
    @NotNull(message = "Media type is required")
    @Pattern(regexp = "^(IMAGE|VIDEO)$", message = "Media type must be IMAGE or VIDEO")
    private String mediaType;
    
    @Size(max = 500, message = "Target URL must not exceed 500 characters")
    @Pattern(regexp = "^(https?://)?.*", message = "Target URL must be a valid URL")
    private String targetUrl;
    
    // Fechas (pueden ser null)
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    
    @NotEmpty(message = "At least one category must be selected")
    @Size(max = 10, message = "Cannot select more than 10 categories")
    private List<Long> categoryIds;
    
    // Ubicaciones (puede estar vacío = todo el país)
    private List<String> targetMunicipalitiesCodes;
    
    @NotNull(message = "Min age is required")
    @Min(value = 13, message = "Min age must be at least 13")
    @Max(value = 100, message = "Min age must not exceed 100")
    private Integer minAge;
    
    @NotNull(message = "Max age is required")
    @Min(value = 13, message = "Max age must be at least 13")
    @Max(value = 100, message = "Max age must not exceed 100")
    private Integer maxAge;
    
    @NotNull(message = "Target gender is required")
    @Pattern(regexp = "^(ALL|MALE|FEMALE)$", message = "Target gender must be ALL, MALE, or FEMALE")
    private String targetGender;
    
    @AssertTrue(message = "Max age must be greater than or equal to min age")
    public boolean isAgeRangeValid() {
        if (minAge == null || maxAge == null) {
            return true; // Let @NotNull handle null validation
        }
        return maxAge >= minAge;
    }
    
    @AssertTrue(message = "End date must be after start date")
    public boolean isDateRangeValid() {
        if (startDate == null || endDate == null) {
            return true; // Dates are optional
        }
        return endDate.isAfter(startDate);
    }
}