package com.verygana2.dtos.wompi;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Estructura del evento que Wompi envía al webhook.
 *
 * Wompi envía un POST con este body:
 * {
 *   "event": "transaction.updated",
 *   "data": {
 *     "transaction": {
 *       "id": "1234-1668097749-23456",
 *       "status": "APPROVED",
 *       "reference": "VG-COP-abc123",
 *       "amount_in_cents": 1000000,
 *       ...
 *     }
 *   },
 *   "environment": "test",
 *   "signature": {
 *     "checksum": "sha256hash",
 *     "properties": ["transaction.id", "transaction.status", "transaction.amount_in_cents"]
 *   },
 *   "timestamp": 1668097749
 * }
 *
 * Referencia: https://docs.wompi.co/docs/colombia/eventos
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WompiWebhookEvent {

    /**
     * Tipo de evento. El único que nos interesa es "transaction.updated".
     * Wompi puede enviar otros tipos en el futuro — los ignoramos.
     */
    @JsonProperty("event")
    private String event;

    @JsonProperty("data")
    private WompiEventData data;

    /**
     * "test" en sandbox, "prod" en producción.
     * Útil para loguear y detectar si un evento de producción llegó
     * a un servidor de staging por error.
     */
    @JsonProperty("environment")
    private String environment;

    @JsonProperty("signature")
    private WompiSignature signature;

    @JsonProperty("timestamp")
    private Long timestamp;

    // ─── Clases internas ──────────────────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WompiEventData {

        @JsonProperty("transaction")
        private WompiTransactionPayload transaction;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WompiTransactionPayload {

        /** ID único de la transacción en Wompi */
        @JsonProperty("id")
        private String id;

        /**
         * Estado resultante.
         * Valores: APPROVED, DECLINED, VOIDED, ERROR
         */
        @JsonProperty("status")
        private String status;

        /** Referencia interna que enviamos al crear el checkout */
        @JsonProperty("reference")
        private String reference;

        @JsonProperty("amount_in_cents")
        private Long amountInCents;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("customer_email")
        private String customerEmail;

        @JsonProperty("payment_method_type")
        private String paymentMethodType;

        @JsonProperty("status_message")
        private String statusMessage;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("finalized_at")
        private String finalizedAt;

        /**
         * Payload completo del método de pago usado.
         * Se guarda en WompiTransaction.metadata como parte del payload completo.
         */
        @JsonProperty("payment_method")
        private Map<String, Object> paymentMethod;

        public boolean isApproved() {
            return "APPROVED".equals(status);
        }

        public boolean isTerminal() {
            return "APPROVED".equals(status)
                    || "DECLINED".equals(status)
                    || "VOIDED".equals(status)
                    || "ERROR".equals(status);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WompiSignature {

        /**
         * Hash SHA-256 que Wompi calcula para validar la autenticidad del evento.
         * Se verifica en WompiWebhookController antes de procesar nada.
         */
        @JsonProperty("checksum")
        private String checksum;

        /**
         * Lista de propiedades que Wompi usó para calcular el checksum.
         * Ejemplo: ["transaction.id", "transaction.status", "transaction.amount_in_cents"]
         * La fórmula es: SHA256(prop1Value + prop2Value + ... + timestamp + eventsKey)
         */
        @JsonProperty("properties")
        private java.util.List<String> properties;
    }
}
