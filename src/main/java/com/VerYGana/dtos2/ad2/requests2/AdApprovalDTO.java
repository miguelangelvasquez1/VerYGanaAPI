package com.VerYGana.dtos2.ad2.requests2;

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
public class AdApprovalDTO {
    
    @NotNull(message = "La decisión de aprobación es obligatoria")
    private Boolean isApproved;
    
    @Size(max = 500, message = "La razón no puede exceder 500 caracteres")
    private String reason; // Requerido si approved = false
}