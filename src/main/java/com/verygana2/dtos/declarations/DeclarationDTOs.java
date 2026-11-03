package com.verygana2.dtos.declarations;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.declarations.DeclarationCode;
import com.verygana2.models.enums.declarations.ManifestationStatus;

/** Respuestas de declaraciones contractuales. */
public final class DeclarationDTOs {

    private DeclarationDTOs() {
    }

    /**
     * Texto literal vigente y la manifestación que ya exista. Si {@code manifestation} viene
     * con estado MANIFESTED, el front la muestra como evidencia existente: no vuelve a pedirla.
     */
    public record DeclarationView(
            DeclarationCode code,
            String version,
            String fullText,
            String textHash,
            Manifestation manifestation) {
    }

    /** Evidencia registrada. Para compliance incluye el actor; el empresario ve lo mismo de sí mismo. */
    public record Manifestation(
            Long id,
            ManifestationStatus status,
            String declarationVersion,
            Long contractId,
            int contractVersion,
            ZonedDateTime occurredAt,
            String detail,
            String actorLegalRepName) {
    }
}
