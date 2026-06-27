package com.verygana2.services.interfaces.compliance;

import java.util.List;

import com.verygana2.models.compliance.ScreeningResult;

public interface ScreeningService {

    /**
     * Corre screening contra todas las listas. Persiste los resultados en TX independiente.
     * Lanza ScreeningHitException si algún resultado es HIT.
     * Los resultados FUZZY_HIT se persisten pero no bloquean la operación.
     */
    void screenOrThrow(Long userId, String nombre, String documentoConsultado);

    List<ScreeningResult> getResultsByUserId(Long userId);

    boolean hasActiveHit(Long userId);

    void reviewResult(Long resultId, Long officerId, String notes);
}