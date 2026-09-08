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
 * 2. Filtrar por tipo de evento ANTES de validar firma: "payout.updated" (estado del
 *    lote, data.payout) no tiene data.transaction, así que ni aplica el algoritmo de
 *    firma de transacción individual — se ignora directo, no se reporta como firma inválida.
 * 3. Validar la firma antes de procesar cualquier dato de un "transaction.updated".
 * 4. Solo procesar "transaction.updated" en estado terminal (el lote se ignora siempre,
 *    procesamos cada Payout individualmente).
 * 5. Procesamiento de negocio en el servicio, no aquí.
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
        log.debug("[WOMPI PAYOUT WEBHOOK] rawBody={}", rawBody);

        // ── 1. Deserializar y filtrar por tipo ANTES de validar firma ──────────
        // "payout.updated" (estado del lote, data.payout) no tiene data.transaction,
        // así que ni siquiera aplica intentar validar su firma con el algoritmo de
        // transacción individual — se ignora de una vez con un log claro, en vez
        // de reportarlo como "firma inválida" (que suena a un problema real).
        Map<String, Object> metadata;
        try {
            metadata = objectMapper.readValue(rawBody, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("[WOMPI PAYOUT WEBHOOK] Error convirtiendo payload a Map: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }

        WompiPayoutWebhookEvent event;
        try {
            event = objectMapper.readValue(rawBody, WompiPayoutWebhookEvent.class);
        } catch (JsonProcessingException e) {
            log.error("[WOMPI PAYOUT WEBHOOK] Error deserializando payload: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }

        if (!event.isTransactionEvent() || event.getData() == null || event.getData().getTransaction() == null) {
            log.debug("[WOMPI PAYOUT WEBHOOK] Evento ignorado (tipo no manejado, no aplica validar firma): {}",
                    event.getEvent());
            return ResponseEntity.ok().build();
        }

        // ── 2. Validar firma ───────────────────────────────────────────────────
        String effectiveChecksum = checksum;
        if (effectiveChecksum == null || effectiveChecksum.isBlank()) {
            effectiveChecksum = extractChecksumFromBody(metadata);
        }

        if (effectiveChecksum == null || effectiveChecksum.isBlank()
                || !wompiPayoutClient.isValidWebhookSignature(rawBody, effectiveChecksum)) {
            log.warn("[WOMPI PAYOUT WEBHOOK] Evento rechazado: firma ausente o inválida");
            return ResponseEntity.ok().build();
        }

        WompiPayoutTransactionPayload payload = event.getData().getTransaction();

        log.info("[WOMPI PAYOUT WEBHOOK] id={}, payoutId={}, status={}, reference={}",
                payload.getId(), payload.getPayoutId(), payload.getStatus(), payload.getReference());

        // ── 3. Solo procesar estados terminales ───────────────────────────────
        if (!payload.isTerminal()) {
            log.debug("[WOMPI PAYOUT WEBHOOK] Estado no terminal ignorado: {}", payload.getStatus());
            return ResponseEntity.ok().build();
        }

        // ── 4. Actualizar WompiTransaction y delegar al servicio ───────────────
        try {
            // Confirmado en sandbox: "reference" no viene poblado en la práctica pese
            // a que el spec público lo documenta — el campo real para correlacionar es
            // "payoutId" (coincide con el wompiId que guardamos al crear el payout).
            // Se deja "reference" como fallback defensivo por si algún ambiente sí lo envía.
            WompiTransaction tx = wompiTransactionRepository.findByWompiId(payload.getPayoutId())
                    .orElseGet(() -> wompiTransactionRepository.findByReference(payload.getReference()).orElse(null));

            if (tx == null) {
                log.warn("[WOMPI PAYOUT WEBHOOK] WompiTransaction no encontrada: payoutId={}, reference={}, id={}",
                        payload.getPayoutId(), payload.getReference(), payload.getId());
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
