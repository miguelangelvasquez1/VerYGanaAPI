package com.verygana2.services.commercial;

import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Component;

import com.verygana2.config.zapsign.ZapSignConfig;
import com.verygana2.dtos.user.commercial.onboarding.EsignatureEnvelope;
import com.verygana2.dtos.user.commercial.onboarding.SignatureRequest;
import com.verygana2.dtos.zapsign.ZapSignCreateDocumentRequestDTO;
import com.verygana2.dtos.zapsign.ZapSignDocumentResponseDTO;
import com.verygana2.dtos.zapsign.ZapSignSignerRequestDTO;
import com.verygana2.services.interfaces.commercial.ESignaturePort;
import com.verygana2.services.zapsign.ZapSignClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador de ESignaturePort para ZapSign (https://docs.zapsign.com.br), único proveedor
 * de firma electrónica. El resultado real de la firma llega vía webhook — ver
 * ZapSignWebhookController.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ZapSignESignatureProvider implements ESignaturePort {

    private static final String PROVIDER_NAME = "zapsign";

    /**
     * Texto ancla insertado en templates/contracts/contrato-marco.html (sección de firmas)
     * donde ZapSign coloca el widget de firma. Debe coincidir exactamente con el texto
     * visible en el PDF generado — si se cambia acá, hay que cambiarlo también en el template.
     */
    private static final String SIGNATURE_PLACEMENT_TAG = "<<{firma_comercial}>>";

    private final ZapSignClient zapSignClient;
    private final ZapSignConfig zapSignConfig;

    @Override
    public EsignatureEnvelope sendForSignature(SignatureRequest request) {
        ZapSignSignerRequestDTO signer = ZapSignSignerRequestDTO.builder()
                .name(request.signerName())
                .email(request.signerEmail())
                .phoneCountry(zapSignConfig.getDefaultPhoneCountryCode())
                .phoneNumber(request.signerPhoneNumber())
                .customMessage(zapSignConfig.getSignerCustomMessage())
                .lockName(true)
                .signaturePlacement(SIGNATURE_PLACEMENT_TAG)
                .authMode(zapSignConfig.getDefaultAuthMode())
                .sendAutomaticEmail(true)
                .requireDocumentPhoto(true)
                .requireSelfiePhoto(true)
                .selfieValidationType(zapSignConfig.isRequireSelfieValidation()
                        ? zapSignConfig.getSelfieValidationType() : null)
                .build();

        ZapSignCreateDocumentRequestDTO documentRequest = ZapSignCreateDocumentRequestDTO.builder()
                .name(request.documentFileName())
                .base64Pdf(Base64.getEncoder().encodeToString(request.documentBytes()))
                .signers(List.of(signer))
                .lang(zapSignConfig.getLang())
                .sandbox(zapSignConfig.isSandbox())
                .brandLogo(zapSignConfig.getBrandLogo())
                .brandName(zapSignConfig.getBrandName())
                .externalId(String.valueOf(request.contractId()))
                .folderPath("/comercios/" + request.commercialId() + "/")
                .build();

        ZapSignDocumentResponseDTO response = zapSignClient.createDocument(documentRequest);

        log.info("[ZAPSIGN] Contrato {} enviado a firma: docToken={}", request.contractId(), response.getToken());

        return new EsignatureEnvelope(response.getToken(), PROVIDER_NAME);
    }
}
