package com.verygana2.dtos.ad.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdRejectDTO {
    
    @NotBlank(message = "La razón de rechazo es obligatoria")
    @Size(max = 500, message = "La razón no puede exceder 500 caracteres")
    private String reason; // Requerido si approved = false
}