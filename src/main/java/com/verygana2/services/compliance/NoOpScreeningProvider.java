package com.verygana2.services.compliance;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.verygana2.models.enums.ScreeningList;
import com.verygana2.models.enums.ScreeningStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementación no-op activa cuando screening.provider=noop (entornos de desarrollo).
 * En producción reemplazar con TruoraScreeningProvider u otro adaptador.
 */
@Component
@ConditionalOnProperty(name = "screening.provider", havingValue = "noop", matchIfMissing = true)
@Slf4j
public class NoOpScreeningProvider implements ScreeningPort {

    @Override
    public List<ScreeningOutcome> screen(String nombre, String documentoConsultado) {
        log.debug("NoOp screening para '{}' — retorna NO_HIT en todas las listas", nombre);
        return Arrays.stream(ScreeningList.values())
                .map(lista -> new ScreeningOutcome(lista, ScreeningStatus.NO_HIT, null, null))
                .toList();
    }
}