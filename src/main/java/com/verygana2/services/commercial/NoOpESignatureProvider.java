package com.verygana2.services.commercial;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.verygana2.services.interfaces.commercial.ESignaturePort;

import lombok.extern.slf4j.Slf4j;

/**
 * No-op implementation active when esignature.provider=noop (development environments).
 * In production, replace with a real adapter (DocuSign, HelloSign, etc.).
 */
@Component
@ConditionalOnProperty(name = "esignature.provider", havingValue = "noop", matchIfMissing = true)
@Slf4j
public class NoOpESignatureProvider implements ESignaturePort {

    @Override
    public EsignatureEnvelope sendForSignature(SignatureRequest request) {
        String envelopeId = "noop-" + UUID.randomUUID();
        log.info("NoOp e-signature: simulando envío a firma del contrato {} a {} (envelopeId={})",
                request.contractId(), request.signerEmail(), envelopeId);
        return new EsignatureEnvelope(envelopeId, "noop");
    }
}
