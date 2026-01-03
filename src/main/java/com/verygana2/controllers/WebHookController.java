package com.verygana2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.services.interfaces.WompiPaymentService;
import com.verygana2.services.interfaces.WompiService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller para recibir webhooks de Wompi
 * 
 * IMPORTANTE: 
 * - Esta URL debe estar configurada en el dashboard de Wompi
 * - NO requiere autenticación (se valida con firma)
 * - DEBE responder 200 OK siempre o Wompi reintentará
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {
    
    private final WompiService wompiService;
    private final WompiPaymentService wompiPaymentService;
    private final ObjectMapper objectMapper;
    
    /**
     * Recibir webhook de Wompi
     * 
     * POST /api/webhooks/wompi
     * 
     * Wompi enviará:
     * Header: X-Event-Checksum (firma HMAC)
     * Header: X-Event-Timestamp
     * Header: X-Event-Sent-At
     * 
     * Body ejemplo:
     * {
     *   "event": "transaction.updated",
     *   "data": {
     *     "transaction": {
     *       "id": "1234-1668097749-23456",
     *       "status": "APPROVED",
     *       "reference": "VERYGANA-DEP-123-1668097749000",
     *       ...
     *     }
     *   },
     *   "sent_at": "2024-01-02T10:30:00.000Z",
     *   "timestamp": 1704189000
     * }
     */
    @PostMapping("/wompi")
    public ResponseEntity<Map<String, Object>> handleWompiWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Event-Checksum", required = false) String signature,
            @RequestHeader(value = "X-Event-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Event-Sent-At", required = false) String sentAt
    ) {
        log.info("📩 Webhook recibido de Wompi");
        log.debug("Payload: {}", rawPayload);
        
        try {
            // 1. VALIDAR FIRMA (CRÍTICO PARA SEGURIDAD)
            if (signature == null || timestamp == null || sentAt == null) {
                log.error("❌ Headers de firma faltantes");
                return buildResponse(false, "Headers de firma faltantes");
            }
            
            boolean isValidSignature = wompiService.validateWebhookSignature(
                    rawPayload,
                    signature,
                    timestamp,
                    sentAt
            );
            
            if (!isValidSignature) {
                log.error("❌ FIRMA INVÁLIDA - Posible intento de fraude");
                // Aún respondemos 200 para evitar reintentos
                return buildResponse(false, "Firma inválida");
            }
            
            log.info("✅ Firma válida");
            
            // 2. PARSEAR PAYLOAD
            JsonNode webhook = objectMapper.readTree(rawPayload);
            String event = webhook.get("event").asText();
            
            log.info("📋 Evento: {}", event);
            
            // 3. PROCESAR SEGÚN TIPO DE EVENTO
            switch (event) {
                case "transaction.updated":
                    handleTransactionUpdated(webhook.get("data").get("transaction"));
                    break;
                    
                default:
                    log.warn("⚠️ Evento no manejado: {}", event);
            }
            
            // 4. RESPONDER 200 OK (importante para Wompi)
            return buildResponse(true, "Webhook procesado");
            
        } catch (Exception e) {
            log.error("❌ Error procesando webhook: {}", e.getMessage(), e);
            
            // Aún respondemos 200 para evitar reintentos infinitos
            // El error se loguea internamente
            return buildResponse(false, "Error interno: " + e.getMessage());
        }
    }
    
    /**
     * Procesar actualización de transacción
     */
    private void handleTransactionUpdated(JsonNode transaction) {
        try {
            String wompiTxId = transaction.get("id").asText();
            String status = transaction.get("status").asText();
            String statusMessage = transaction.has("status_message") 
                    ? transaction.get("status_message").asText() 
                    : "";
            
            log.info("🔄 Transacción actualizada - ID: {}, Estado: {}", wompiTxId, status);
            
            // Procesar según estado
            switch (status.toUpperCase()) {
                case "APPROVED":
                    // ✅ PAGO EXITOSO
                    log.info("✅ Pago APROBADO: {}", wompiTxId);
                    wompiPaymentService.confirmDeposit(wompiTxId);
                    
                    // TODO: Enviar notificación push al usuario
                    // TODO: Enviar email de confirmación
                    break;
                    
                case "DECLINED":
                    // ❌ PAGO RECHAZADO
                    log.warn("❌ Pago RECHAZADO: {} - Razón: {}", wompiTxId, statusMessage);
                    wompiPaymentService.declineDeposit(wompiTxId, statusMessage);
                    
                    // TODO: Notificar al usuario del rechazo
                    break;
                    
                case "VOIDED":
                    // ⚠️ PAGO CANCELADO
                    log.warn("⚠️ Pago CANCELADO: {}", wompiTxId);
                    wompiPaymentService.declineDeposit(wompiTxId, "Transacción cancelada");
                    
                    // TODO: Notificar cancelación
                    break;
                    
                case "ERROR":
                    // ❌ ERROR EN PROCESAMIENTO
                    log.error("❌ ERROR en pago: {} - {}", wompiTxId, statusMessage);
                    wompiPaymentService.declineDeposit(wompiTxId, "Error: " + statusMessage);
                    break;
                    
                case "PENDING":
                    // ⏳ AÚN PENDIENTE (PSE/Nequi)
                    log.info("⏳ Pago aún PENDIENTE: {}", wompiTxId);
                    // No hacer nada, ya está en estado PENDING
                    break;
                    
                default:
                    log.warn("⚠️ Estado no reconocido: {}", status);
            }
            
        } catch (Exception e) {
            log.error("❌ Error procesando actualización de transacción: {}", e.getMessage(), e);
            // No lanzar excepción para que el webhook se marque como procesado
        }
    }
    
    /**
     * Construir respuesta estándar
     */
    private ResponseEntity<Map<String, Object>> buildResponse(boolean success, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("received", true);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Endpoint para verificar que el webhook está funcionando
     * Útil para testing desde el dashboard de Wompi
     * 
     * GET /api/webhooks/wompi/health
     */
    @GetMapping("/wompi/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("service", "wompi-webhook");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
}
