package com.verygana2.dtos.ad.requests;

import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.models.enums.TargetGender;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
    
    // rewardPerLike ELIMINADO — no se permite editar
    
    @Min(value = 1, message = "Debe permitir al menos 1 like")
    private Integer maxLikes;
    
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    
    @Size(max = 500)
    private String targetUrl;
    
    private List<Long> categoryIds;
    private List<String> targetMunicipalitiesCodes;
    private Integer minAge;
    private Integer maxAge;
    
    @NotNull(message = "This spec is required")
    private TargetGender targetGender;
}