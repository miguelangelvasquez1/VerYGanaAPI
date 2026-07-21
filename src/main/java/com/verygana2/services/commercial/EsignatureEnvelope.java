package com.verygana2.services.commercial;

/** Referencia del "sobre" (envelope) creado por el proveedor de firma electrónica. */
public record EsignatureEnvelope(String envelopeId, String provider) {
}
