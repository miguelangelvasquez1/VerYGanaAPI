package com.verygana2.controllers.wompi;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.wompi.WompiWebhookEvent;
import com.verygana2.dtos.wompi.WompiWebhookEvent.WompiTransactionPayload;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.services.wompi.WompiClient;
import com.verygana2.services.wompi.WompiService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Endpoint que recibe todos los eventos de Wompi vía webhook.
 *
 * REGLAS DE ORO para este controller:
 *
 * 1. SIEMPRE responder 200 OK a Wompi, incluso si hay un error interno.
 *    Si respondes 4xx o 5xx, Wompi reintenta el webhook múltiples veces
 *    durante horas, lo que puede causar procesamiento duplicado.
 *    Los errores se loguean y se manejan internamente.
 *
 * 2. SIEMPRE validar la firma antes de procesar cualquier dato.
 *    Un webhook sin firma válida se ignora silenciosamente.
 *
 * 3. SIEMPRE verificar idempotencia: si el wompiId ya fue procesado,
 *    ignorar el evento. Wompi puede enviar el mismo evento más de una vez.
 *
 * 4. El procesamiento pesado (copago, tesorería) ocurre en los servicios,
 *    no aquí. Este controller solo valida, enruta y responde rápido.
 */
@Slf4j
@RestController
@RequestMapping("/wompi")
@RequiredArgsConstructor
public class WompiWebhookController {

    private static final String TRANSACTION_UPDATED_EVENT = "transaction.updated";

    private final WompiClient wompiClient;
    private final WompiService wompiService;
    private final WompiWebhookDispatcher webhookDispatcher;
    private final ObjectMapper objectMapper;

    /**
     * Endpoint principal que recibe todos los eventos de Wompi.
     *
     * Wompi envía el header "x-event-checksum" con la firma SHA-256.
     * La firma se calcula así:
     *   SHA256(prop1Value + prop2Value + ... + timestamp + eventsKey)
     * donde las propiedades están listadas en event.signature.properties.
     *
     * @param rawBody   cuerpo del webhook como String (necesario para validar firma)
     * @param checksum  header "x-event-checksum" enviado por Wompi
     */
    @PostMapping("/events")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "x-event-checksum", required = false) String checksum) {

        log.info("[WEBHOOK] Evento recibido de Wompi");

        // ── 1. Validar firma ──────────────────────────────────────────────────
        if (checksum == null || checksum.isBlank()) {
            log.warn("[WEBHOOK] Evento rechazado: header x-event-checksum ausente");
            return ResponseEntity.ok().build(); // 200 de todas formas (ver regla 1)
        }

        if (!wompiClient.isValidWebhookSignature(rawBody, checksum)) {
            log.warn("[WEBHOOK] Evento rechazado: firma inválida. checksum={}", checksum);
            return ResponseEntity.ok().build();
        }

        // ── 2. Deserializar el payload ────────────────────────────────────────
        WompiWebhookEvent event;
        try {
            event = objectMapper.readValue(rawBody, WompiWebhookEvent.class);
        } catch (JsonProcessingException e) {
            log.error("[WEBHOOK] Error deserializando payload: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }

        log.info("[WEBHOOK] Evento válido: type={}, environment={}",
                event.getEvent(), event.getEnvironment());

        // ── 3. Filtrar solo eventos de transacciones ──────────────────────────
        if (!TRANSACTION_UPDATED_EVENT.equals(event.getEvent())) {
            log.debug("[WEBHOOK] Evento ignorado (tipo no manejado): {}", event.getEvent());
            return ResponseEntity.ok().build();
        }

        if (event.getData() == null || event.getData().getTransaction() == null) {
            log.warn("[WEBHOOK] Evento transaction.updated sin datos de transacción");
            return ResponseEntity.ok().build();
        }

        WompiTransactionPayload txPayload = event.getData().getTransaction();

        // ── 4. Idempotencia: ignorar si ya fue procesado ──────────────────────
        if (wompiService.isAlreadyProcessed(txPayload.getId())) {
            log.info("[WEBHOOK] Evento duplicado ignorado: wompiId={}", txPayload.getId());
            return ResponseEntity.ok().build();
        }

        // ── 5. Solo procesar estados terminales ───────────────────────────────
        // PENDING no es terminal — Wompi enviará otro webhook cuando se resuelva.
        if (!txPayload.isTerminal()) {
            log.info("[WEBHOOK] Estado no terminal ignorado: wompiId={}, status={}",
                    txPayload.getId(), txPayload.getStatus());
            return ResponseEntity.ok().build();
        }

        // ── 6. Actualizar WompiTransaction con datos del webhook ──────────────
        Map<String, Object> metadata;
        try {
            metadata = objectMapper.readValue(rawBody, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("[WEBHOOK] Error convirtiendo payload a Map: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }

        WompiTransaction updatedTx = wompiService.updateTransactionFromWebhook(
                txPayload.getId(),
                txPayload.getReference(),
                txPayload.getStatus(),
                metadata
        );

        // ── 7. Despachar al servicio de negocio correspondiente ───────────────
        // El dispatcher decide si es un copago, un pago de plan, etc.
        // Se ejecuta de forma asíncrona para responder a Wompi lo más rápido posible.
        webhookDispatcher.dispatch(updatedTx, txPayload);

        log.info("[WEBHOOK] Procesado exitosamente: wompiId={}, reference={}, status={}",
                txPayload.getId(), txPayload.getReference(), txPayload.getStatus());

        return ResponseEntity.ok().build();
    }
}
