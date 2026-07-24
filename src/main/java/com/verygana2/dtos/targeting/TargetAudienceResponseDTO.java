package com.verygana2.dtos.targeting;

import java.util.List;

import com.verygana2.models.enums.TargetGender;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Espejo de {@link OptionalTargetAudienceDTO} para lectura: mismos nombres de
 * campo, así el frontend puede precargar el formulario de edición con la
 * misma forma que usa para enviarlo. Vacío/null en cada campo = sin
 * restricción en esa dimensión.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetAudienceResponseDTO {
    private List<String> municipalityCodes;
    private Integer minAge;
    private Integer maxAge;
    private TargetGender targetGender;
}
