package com.verygana2.services.interfaces.compliance;

import java.util.List;

import com.verygana2.models.compliance.ScreeningResult;

public interface ScreeningService {

    /**
     * Runs screening against all lists. Persists results in an independent TX.
     * Throws ScreeningHitException if any result is HIT.
     * FUZZY_HIT results are persisted but do not block the operation.
     */
    void screenOrThrow(Long userId, String name, String queriedDocument);

    List<ScreeningResult> getResultsByUserId(Long userId);

    boolean hasActiveHit(Long userId);

    void reviewResult(Long resultId, Long officerId, String notes);
}