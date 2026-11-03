package com.verygana2.dtos.user.commercial.onboarding;

import com.verygana2.models.enums.declarations.ManifestationDecision;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Aprobación del Contrato Marco por el empresario, con la casilla del apartado 9 de la Hoja
 * Final (compromiso de formalización, 4.7). La casilla no tiene valor por defecto: si
 * {@code decision} no viene, se registra que no hubo manifestación. En ningún caso se
 * bloquea la aprobación.
 */
@Data
public class ContractApproveRequestDTO {

    /** Versión del texto que el empresario vio en la Hoja Final. */
    @Size(max = 20)
    private String declarationVersion;

    /** ACCEPT = marcó la casilla; REFUSE = se negó expresamente; null = no se manifestó. */
    private ManifestationDecision decision;

    /** Motivo opcional de la negativa. */
    @Size(max = 1000)
    private String refusalReason;
}
