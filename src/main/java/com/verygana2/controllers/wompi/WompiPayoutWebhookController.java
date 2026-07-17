package com.verygana2.controllers.wompi;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.wompi.WompiPayoutWebhookEvent;
import com.verygana2.dtos.wompi.WompiPayoutWebhookEvent.WompiPayoutTransactionPayload;
import com.verygana2.models.enums.finance.WompiTransactionStatus;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.repositories.finance.WompiTransactionRepository;
import com.verygana2.services.interfaces.finance.PayoutService;
import com.verygana2.services.wompi.WompiPayoutClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Recibe confirmaciones de Wompi Pagos a Terceros cuando una transferencia
 * cambia de estado. Endpoint separado de /wompi/events (cobros) porque el
 * payload y la eventsKey de este producto son distintos — ver WompiPayoutConfig.
 *
 * REGLAS DE ORO (mismas que WompiWebhookController / antiguo KushkiWebhookController):
 * 1. Siempre responder 200 OK — si devuelves 4xx/5xx Wompi reintenta hasta 3 veces.
 * 2. Validar la firma antes de procesar cualquier dato.
 * 3. Solo procesar "transaction.updated" en estado terminal (ignoramos "payout.updated",
 *    que es el estado del lote completo — procesamos cada Payout individualmente).
 * 4. Procesamiento de negocio en el servicio, no aquí.
 */
@Slf4j
@RestController
@RequestMapping("/wompi/payouts")
@RequiredArgsConstructor
public class WompiPayoutWebhookController {

    private final WompiPayoutClient wompiPayoutClient;
    private final WompiTransactionRepository wompiTransactionRepository;
    private final PayoutService payoutService;
    private final ObjectMapper objectMapper;

    @PostMapping("/events")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "x-event-checksum", required = false) String checksum) {

        log.info("[WOMPI PAYOUT WEBHOOK] Evento recibido");

        // ── 1. Validar firma ──────────────────────────────────────────────────
        String effectiveChecksum = checksum;
        Map<String, Object> metadata;
        try {
            metadata = objectMapper.readValue(rawBody, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("[WOMPI PAYOUT WEBHOOK] Error convirtiendo payload a Map: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }

        if (effectiveChecksum == null || effectiveChecksum.isBlank()) {
            effectiveChecksum = extractChecksumFromBody(metadata);
        }

        if (effectiveChecksum == null || effectiveChecksum.isBlank()
                || !wompiPayoutClient.isValidWebhookSignature(rawBody, effectiveChecksum)) {
            log.warn("[WOMPI PAYOUT WEBHOOK] Evento rechazado: firma ausente o inválida");
            return ResponseEntity.ok().build();
        }

        // ── 2. Deserializar ───────────────────────────────────────────────────
        WompiPayoutWebhookEvent event;
        try {
            event = objectMapper.readValue(rawBody, WompiPayoutWebhookEvent.class);
        } catch (JsonProcessingException e) {
            log.error("[WOMPI PAYOUT WEBHOOK] Error deserializando payload: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }

        // ── 3. Solo nos interesa transaction.updated ────────────────────────────
        if (!event.isTransactionEvent() || event.getData() == null || event.getData().getTransaction() == null) {
            log.debug("[WOMPI PAYOUT WEBHOOK] Evento ignorado (tipo no manejado): {}", event.getEvent());
            return ResponseEntity.ok().build();
        }

        WompiPayoutTransactionPayload payload = event.getData().getTransaction();

        log.info("[WOMPI PAYOUT WEBHOOK] id={}, status={}, reference={}",
                payload.getId(), payload.getStatus(), payload.getReference());

        // ── 4. Solo procesar estados terminales ───────────────────────────────
        if (!payload.isTerminal()) {
            log.debug("[WOMPI PAYOUT WEBHOOK] Estado no terminal ignorado: {}", payload.getStatus());
            return ResponseEntity.ok().build();
        }

        // ── 5. Actualizar WompiTransaction y delegar al servicio ───────────────
        try {
            WompiTransaction tx = wompiTransactionRepository.findByReference(payload.getReference())
                    .orElseGet(() -> wompiTransactionRepository.findByWompiId(payload.getId()).orElse(null));

            if (tx == null) {
                log.warn("[WOMPI PAYOUT WEBHOOK] WompiTransaction no encontrada: reference={}, id={}",
                        payload.getReference(), payload.getId());
                return ResponseEntity.ok().build();
            }

            tx.setStatus(mapStatus(payload));
            tx.setMetadata(metadata);
            tx.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
            tx = wompiTransactionRepository.save(tx);

            payoutService.handleWompiResult(tx.getId());

        } catch (Exception e) {
            // Loguear pero siempre responder 200 para evitar reintentos infinitos
            log.error("[WOMPI PAYOUT WEBHOOK] Error procesando evento id={}: {}",
                    payload.getId(), e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    private WompiTransactionStatus mapStatus(WompiPayoutTransactionPayload payload) {
        if (payload.isApproved()) return WompiTransactionStatus.APPROVED;
        if ("DECLINED".equalsIgnoreCase(payload.getStatus())) return WompiTransactionStatus.DECLINED;
        // "FAILED" (Pagos a Terceros) no tiene equivalente exacto en WompiTransactionStatus
        // (pensado originalmente para cobros); lo tratamos como ERROR.
        return WompiTransactionStatus.ERROR;
    }

    @SuppressWarnings("unchecked")
    private String extractChecksumFromBody(Map<String, Object> event) {
        Object signatureObj = event.get("signature");
        if (!(signatureObj instanceof Map)) return null;
        Object checksumObj = ((Map<String, Object>) signatureObj).get("checksum");
        return checksumObj != null ? checksumObj.toString() : null;
    }
}
