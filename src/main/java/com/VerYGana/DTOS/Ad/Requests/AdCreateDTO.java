package com.VerYGana.dtos.ad.Requests;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.VerYGana.models.Enums.Preference;

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
    
    @NotNull(message = "El presupuesto total es obligatorio")
    @DecimalMin(value = "1.00", message = "El presupuesto debe ser mayor a 0")
    private BigDecimal totalBudget;
    
    private LocalDateTime startDate;
    
    private LocalDateTime endDate;
    
    @Size(max = 500, message = "La URL de imagen no puede exceder 500 caracteres")
    private String contentUrl;
    
    @Size(max = 500, message = "La URL de destino no puede exceder 500 caracteres")
    private String targetUrl;
    
    private Preference category;
}