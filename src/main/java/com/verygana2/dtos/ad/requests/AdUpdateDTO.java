package com.verygana2.dtos.ad.requests;

import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class AdUpdateDTO {
    
    @Size(min = 5, max = 100, message = "El título debe tener entre 5 y 100 caracteres")
    private String title;
    
    @Size(min = 10, max = 1000, message = "La descripción debe tener entre 10 y 1000 caracteres")
    private String description;
    
    private ZonedDateTime startDate;
 
    @Size(max = 500, message = "Target URL must not exceed 500 characters")
    @URL(message = "Target URL must be a valid URL")
    private String targetUrl;
    
    @NotEmpty(message = "At least one category must be selected")
    @Size(max = 10, message = "Cannot select more than 10 categories")
    private List<Long> categoryIds;

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
            return true;
        }
        return maxAge >= minAge;
    }
}