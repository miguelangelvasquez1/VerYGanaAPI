package com.verygana2.dtos.ad.requests;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.models.Category;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    
    @DecimalMin(value = "0.01", message = "La recompensa debe ser mayor a 0")
    @DecimalMax(value = "100.00", message = "La recompensa no puede exceder 100")
    private BigDecimal rewardPerLike;
    
    @Min(value = 1, message = "Debe permitir al menos 1 like")
    @Max(value = 10000, message = "No puede exceder 10,000 likes")
    private Integer maxLikes;
    
    @DecimalMin(value = "1.00", message = "El presupuesto debe ser mayor a 0")
    private BigDecimal totalBudget;
    
    private ZonedDateTime startDate;
    
    private ZonedDateTime endDate;
    
    @Size(max = 500, message = "La URL de imagen no puede exceder 500 caracteres")
    private String contentUrl;
    
    @Size(max = 500, message = "La URL de destino no puede exceder 500 caracteres")
    private String targetUrl;
    
    private List<Category> categories;
}
