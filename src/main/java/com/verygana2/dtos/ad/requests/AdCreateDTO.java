package com.verygana2.dtos.ad.requests;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.models.enums.MediaType;
import com.verygana2.models.enums.TargetGender;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdCreateDTO {
    
    @NotBlank(message = "El título es obligatorio")
    @Size(min = 5, max = 100, message = "El título debe tener entre 5 y 100 caracteres")
    private String title;
    
    @NotBlank(message = "La descripción es obligatoria")
    @Size(min = 10, max = 1000, message = "La descripción debe tener entre 10 y 1000 caracteres")
    private String description;
    
    @NotNull(message = "La recompensa por like es obligatoria")
    @DecimalMin(value = "0.01", message = "La recompensa debe ser mayor a 0")
    @DecimalMax(value = "100.00", message = "La recompensa no puede exceder 100")
    private BigDecimal rewardPerLike;
    
    @NotNull(message = "El máximo de likes es obligatorio")
    @Min(value = 1, message = "Debe permitir al menos 1 like")
    @Max(value = 10000, message = "No puede exceder 10,000 likes")
    private Integer maxLikes;
    
    private ZonedDateTime startDate;
    
    private ZonedDateTime endDate;
    
    private MediaType mediaType;
    
    @Size(max = 500, message = "La URL de destino no puede exceder 500 caracteres")
    private String targetUrl;
    
    private List<Long> categoryIds;

    private List<String> targetMunicipalitiesCodes;

    private Integer minAge;

    private Integer maxAge;

    @NotNull(message = "This espec is required")
    private TargetGender targetGender;
}