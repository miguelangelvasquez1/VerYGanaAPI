package com.verygana2.controllers.zapsign;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.config.zapsign.ZapSignConfig;
import com.verygana2.dtos.zapsign.ZapSignWebhookEventDTO;
import com.verygana2.exceptions.commercial.OnboardingStepException;
import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.repositories.commercial.CommercialContractRepository;
import com.verygana2.services.interfaces.commercial.ESignatureService;
import com.verygana2.services.interfaces.compliance.BackgroundCheckService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Endpoint que recibe los eventos de firma de ZapSign.
 *
 * ZapSign no firma sus webhooks con HMAC; la autenticidad se valida con un header
 * custom fijo, configurado al registrar el webhook (ver ZapSignConfig.webhookHeaderName/Secret).
 * Ref: https://docs.zapsign.com.br/english/webhooks/como-funciona
 */
@Slf4j
@RestController
@RequestMapping("/zapsign")
@RequiredArgsConstructor
public class ZapSignWebhookController {

    private static final String DOC_SIGNED_EVENT = "doc_signed";
    private static final String SIGNED_STATUS = "signed";
    private static final String BACKGROUND_CHECK_COMPLETED_EVENT = "background_check_completed";

    private final ZapSignConfig zapSignConfig;
    private final CommercialContractRepository contractRepository;
    private final ESignatureService esignatureService;
    private final BackgroundCheckService backgroundCheckService;
    private final ObjectMapper objectMapper;

    @PostMapping("/events")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader HttpHeaders headers) {

        log.info("[ZAPSIGN WEBHOOK] Evento recibido");

        if (!isValidSecret(headers.getFirst(zapSignConfig.getWebhookHeaderName()))) {
            log.warn("[ZAPSIGN WEBHOOK] Evento rechazado: header secreto ausente o inválido");
            return ResponseEntity.ok().build();
        }

        ZapSignWebhookEventDTO event;
        try {
            event = objectMapper.readValue(rawBody, ZapSignWebhookEventDTO.class);
        } catch (Exception e) {
            log.error("[ZAPSIGN WEBHOOK] Error deserializando payload: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }

        log.info("[ZAPSIGN WEBHOOK] eventType={}, docToken={}, checkId={}, status={}",
                event.getEventType(), event.getToken(), event.getCheckId(), event.getStatus());

        if (BACKGROUND_CHECK_COMPLETED_EVENT.equals(event.getEventType())) {
            try {
                backgroundCheckService.handleWebhookCompleted(event.getCheckId());
            } catch (Exception e) {
                log.error("[ZAPSIGN WEBHOOK] Error procesando background_check_completed: checkId={}, error={}",
                        event.getCheckId(), e.getMessage());
            }
            return ResponseEntity.ok().build();
        }

        if (!DOC_SIGNED_EVENT.equals(event.getEventType()) || !SIGNED_STATUS.equals(event.getStatus())) {
            log.debug("[ZAPSIGN WEBHOOK] Evento ignorado (no es doc_signed con status=signed, ni background_check_completed)");
            return ResponseEntity.ok().build();
        }

        contractRepository.findByEsignatureEnvelopeId(event.getToken()).ifPresentOrElse(
                contract -> markSigned(contract),
                () -> log.warn("[ZAPSIGN WEBHOOK] Ningún contrato con envelopeId={}", event.getToken()));

        return ResponseEntity.ok().build();
    }

    private void markSigned(CommercialContract contract) {
        try {
            esignatureService.markSigned(contract.getId(), ZonedDateTime.now());
        } catch (OnboardingStepException e) {
            log.info("[ZAPSIGN WEBHOOK] Evento duplicado ignorado: contractId={}", contract.getId());
        }
    }

    private boolean isValidSecret(String secretHeader) {
        String expected = zapSignConfig.getWebhookSecret();
        if (expected == null || expected.isBlank() || secretHeader == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                secretHeader.getBytes(StandardCharsets.UTF_8));
    }
}
