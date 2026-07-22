package com.verygana2.services.interfaces.commercial;

import com.verygana2.services.commercial.EsignatureEnvelope;
import com.verygana2.services.commercial.SignatureRequest;

public interface ESignaturePort {

    /**
     * Envía el documento al proveedor de firma electrónica para que el firmante lo firme.
     * El resultado real de la firma llega después, de forma asíncrona (webhook del proveedor).
     */
    EsignatureEnvelope sendForSignature(SignatureRequest request);
}
