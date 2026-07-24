package com.verygana2.utils.validators;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.verygana2.dtos.targeting.OptionalTargetAudienceDTO;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.models.Municipality;
import com.verygana2.models.TargetAudience;
import com.verygana2.models.enums.Gender;
import com.verygana2.models.enums.TargetGender;
import com.verygana2.models.userDetails.ConsumerDetails;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TargetAudienceAssembler {

    private final TargetingValidator targetingValidator;

    /** Construye un TargetAudience nuevo a partir del DTO, para creación. */
    public TargetAudience build(OptionalTargetAudienceDTO dto) {
        TargetAudience ta = new TargetAudience();
        applyTo(ta, dto);
        return ta;
    }

    /**
     * Sobrescribe TODOS los campos de segmentación de un TargetAudience existente
     * con el estado del DTO (reemplazo completo, no patch parcial): el
     * formulario siempre envía el estado deseado completo, así que un campo en
     * null significa "quitar esa restricción". Se muta la instancia recibida
     * in-place para no dejar filas huérfanas (la relación no tiene
     * orphanRemoval).
     */
    public void applyTo(TargetAudience ta, OptionalTargetAudienceDTO dto) {
        if (dto == null) {
            ta.setTargetMunicipalities(Collections.emptyList());
            ta.setMinAge(null);
            ta.setMaxAge(null);
            ta.setTargetGender(null);
            return;
        }

        List<String> municipalityCodes = dto.getMunicipalityCodes();
        ta.setTargetMunicipalities(
                (municipalityCodes == null || municipalityCodes.isEmpty())
                        ? Collections.emptyList()
                        : targetingValidator.getValidatedMunicipalities(municipalityCodes));

        ta.setMinAge(dto.getMinAge());
        ta.setMaxAge(dto.getMaxAge());
        ta.setTargetGender(dto.getTargetGender());
    }

    /**
     * Validación dura de elegibilidad: lanza BusinessException si el consumidor
     * no puede participar según la localidad, edad o género configurados en el
     * TargetAudience. Un TargetAudience nulo, o cada campo individual en
     * null/vacío, significa "sin restricción" en esa dimensión.
     */
    public void validateEligibility(ConsumerDetails consumer, TargetAudience ta, String entityLabel) {
        if (ta == null) {
            return;
        }

        List<Municipality> allowedMunicipalities = ta.getTargetMunicipalities();
        if (allowedMunicipalities != null && !allowedMunicipalities.isEmpty()) {
            String consumerMunicipalityCode = consumer.getMunicipality().getCode();
            boolean allowed = allowedMunicipalities.stream()
                    .anyMatch(m -> m.getCode().equals(consumerMunicipalityCode));
            if (!allowed) {
                throw new BusinessException(entityLabel + " no está disponible en tu localidad");
            }
        }

        Integer consumerAge = consumer.getAge();
        if (consumerAge != null) {
            if (ta.getMinAge() != null && consumerAge < ta.getMinAge()) {
                throw new BusinessException(entityLabel + " no está disponible para tu rango de edad");
            }
            if (ta.getMaxAge() != null && consumerAge > ta.getMaxAge()) {
                throw new BusinessException(entityLabel + " no está disponible para tu rango de edad");
            }
        }

        TargetGender targetGender = ta.getTargetGender();
        Gender consumerGender = consumer.getGender();
        if (targetGender != null && targetGender != TargetGender.ALL && consumerGender != null) {
            boolean genderMatches = targetGender.name().equals(consumerGender.name());
            if (!genderMatches) {
                throw new BusinessException(entityLabel + " no está disponible para tu género");
            }
        }
    }
}
