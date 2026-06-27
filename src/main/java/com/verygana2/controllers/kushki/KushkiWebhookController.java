package com.verygana2.controllers.kushki;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.kushki.KushkiWebhookEvent;
import com.verygana2.services.interfaces.finance.PayoutService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Recibe confirmaciones de Kushki cuando una transferencia cambia de estado.
 *
 * REGLAS DE ORO (mismas que WompiWebhookController):
 * 1. Siempre responder 200 OK — si devuelves 4xx/5xx Kushki reintenta indefinidamente.
 * 2. Verificar idempotencia: si el transferId ya fue procesado, ignorar.
 * 3. Solo procesar estados terminales (APPROVED, DECLINED, FAILED).
 * 4. Procesamiento pesado en el servicio, no aquí.
 *
 * Nota: Kushki no firma sus webhooks con HMAC en todos los planes.
 * Si tu plan incluye firma, agregar validación aquí con el header correspondiente.
 */
@Slf4j
@RestController
@RequestMapping("/kushki")
@RequiredArgsConstructor
public class KushkiWebhookController {

    private final PayoutService payoutService;
    private final ObjectMapper objectMapper;

    @PostMapping("/events")
    public ResponseEntity<Void> handleWebhook(@RequestBody String rawBody) {

        log.info("[KUSHKI WEBHOOK] Evento recibido");

        // ── 1. Deserializar ───────────────────────────────────────────────────
        KushkiWebhookEvent event;
        try {
            event = objectMapper.readValue(rawBody, KushkiWebhookEvent.class);
        } catch (JsonProcessingException e) {
            log.error("[KUSHKI WEBHOOK] Error deserializando payload: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }

        log.info("[KUSHKI WEBHOOK] transferId={}, status={}, reference={}",
                event.getTransferId(), event.getStatus(), event.getMerchantTransferReference());

        // ── 2. Solo procesar estados terminales ───────────────────────────────
        if (!event.isTerminal()) {
            log.debug("[KUSHKI WEBHOOK] Estado no terminal ignorado: {}", event.getStatus());
            return ResponseEntity.ok().build();
        }

        // ── 3. Convertir rawBody a Map para metadata ──────────────────────────
        Map<String, Object> metadata;
        try {
            metadata = objectMapper.readValue(rawBody, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("[KUSHKI WEBHOOK] Error convirtiendo payload a Map: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }

        // ── 4. Delegar al servicio ────────────────────────────────────────────
        try {
            payoutService.handleKushkiWebhook(event, metadata);
        } catch (Exception e) {
            // Loguear pero siempre responder 200 para evitar reintentos infinitos
            log.error("[KUSHKI WEBHOOK] Error procesando evento transferId={}: {}",
                    event.getTransferId(), e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }
}
