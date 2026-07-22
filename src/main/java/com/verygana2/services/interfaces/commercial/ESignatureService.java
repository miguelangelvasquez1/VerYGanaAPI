package com.verygana2.services.interfaces.commercial;

import java.time.ZonedDateTime;

public interface ESignatureService {

    /** Envía el contrato al proveedor de firma electrónica y mueve el onboarding a SIGNATURE_PENDING. */
    void requestSignature(Long contractId);

    /**
     * Registra la firma del contrato. Hoy se dispara manualmente desde compliance porque no
     * hay un proveedor real todavía; en producción lo llamará el webhook del proveedor una vez
     * resuelva envelopeId -> contractId. Marca el contrato SIGNED y el onboarding PAYMENT_PENDING.
     */
    void markSigned(Long contractId, ZonedDateTime signedAt);
}
