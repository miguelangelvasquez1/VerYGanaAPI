package com.verygana2.dtos.user.commercial.onboarding;

/** Solicitud de envío a firma electrónica de un Contrato Marco. */
public record SignatureRequest(Long contractId, Long commercialId, String signerName, String signerEmail,
        String signerPhoneNumber, byte[] documentBytes, String documentFileName) {
}
