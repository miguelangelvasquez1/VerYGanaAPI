package com.verygana2.utils.validators;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.verygana2.models.Municipality;
import com.verygana2.repositories.MunicipalityRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TargetingValidator {

    private final MunicipalityRepository municipalityRepository;
    
    // Validar códigos de municipios
    public List<Municipality> getValidatedMunicipalities(List<String> codes) {

        List<Municipality> targetMunicipalities = new ArrayList<>();
        if (!codes.isEmpty()) {
            targetMunicipalities = municipalityRepository.findAllById(codes);

            if (targetMunicipalities.size() != codes.size()) {
                throw new IllegalArgumentException("Algunos códigos de municipio no existen");
            }
        }
        return targetMunicipalities;
    }
}
