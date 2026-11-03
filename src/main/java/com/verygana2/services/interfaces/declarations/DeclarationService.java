package com.verygana2.services.interfaces.declarations;

import java.util.List;
import java.util.UUID;

import com.verygana2.dtos.declarations.DeclarationDTOs.DeclarationView;
import com.verygana2.dtos.declarations.DeclarationDTOs.Manifestation;
import com.verygana2.dtos.user.commercial.onboarding.ContractApproveRequestDTO;
import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.declarations.DeclarationText;
import com.verygana2.models.enums.declarations.DeclarationCode;
import com.verygana2.models.enums.declarations.DisclosureContext;

/**
 * Declaraciones contractuales (1.7 / 4.7): texto literal versionado, su puesta a disposición
 * y la manifestación recogida una sola vez en la Hoja Final del contrato.
 */
public interface DeclarationService {

    /** Texto vigente. Lanza si no hay ninguno activo (la migración siembra el primero). */
    DeclarationText activeText(DeclarationCode code);

    /** Muestra el texto y la evidencia existente, y registra que se puso a disposición. No acepta nada. */
    DeclarationView viewForCommercial(Long commercialId, DeclarationCode code, DisclosureContext context);

    /** Manifestación vigente del empresario: la expresa si existe; si no, la más reciente. Null si nunca hubo. */
    Manifestation currentManifestation(Long commercialId, DeclarationCode code);

    /**
     * Registra lo que pasó con la casilla del apartado 9 al aprobar el contrato. Nunca lanza
     * por la decisión: la aprobación del contrato sigue en todos los casos. Si ya hay una
     * manifestación expresa para este contrato y texto, o la clave ya se usó, la reutiliza.
     */
    Manifestation recordContractManifestation(CommercialOnboarding onboarding, CommercialContract contract,
            ContractApproveRequestDTO request, String idempotencyKey, Long actorUserId, String ipAddress,
            String userAgent);

    List<Manifestation> historyForCompliance(UUID commercialPublicId, DeclarationCode code);
}
