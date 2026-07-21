package com.verygana2.services.commercial;

/** Solicitud de envío a firma electrónica de un Contrato Marco. */
public record SignatureRequest(Long contractId, String signerName, String signerEmail,
        byte[] documentBytes, String documentFileName) {
}
