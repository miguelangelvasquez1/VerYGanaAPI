package com.verygana2.dtos.wompi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Estructura del evento que Wompi envía al webhook de Pagos a Terceros.
 * Distinto del webhook de cobros (WompiWebhookEvent): usa camelCase y no
 * comparte la forma de "data.transaction" (sin customer_email/payment_method,
 * con failureReason). Por eso vive en un DTO y endpoint separados.
 *
 * Wompi envía dos tipos de evento en este producto:
 * - "payout.updated": estado del lote completo (data.payout) — lo ignoramos,
 *   procesamos cada Payout de forma individual, no por lote.
 * - "transaction.updated": estado de una transferencia individual
 *   (data.transaction) — es el que nos interesa.
 *
 * {
 *   "event": "transaction.updated",
 *   "data": {
 *     "transaction": {
 *       "id": "04a6e53d-a244-4140-ab9e-48fa541f9fe5",
 *       "reference": "VG-PAYOUT-abc123",
 *       "status": "FAILED",
 *       "amountInCents": 7500000,
 *       "failureReason": { "code": "C01", "message": "La cuenta no existe o está inactiva" }
 *     }
 *   },
 *   "signature": { "checksum": "...", "properties": ["transaction.id", "transaction.status"] },
 *   "timestamp": 1747673128600
 * }
 *
 * Referencia: https://docs.wompi.co/docs/colombia/eventos-pagos-a-terceros/
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WompiPayoutWebhookEvent {

    @JsonProperty("event")
    private String event;

    @JsonProperty("data")
    private WompiPayoutEventData data;

    @JsonProperty("environment")
    private String environment;

    @JsonProperty("timestamp")
    private Long timestamp;

    public boolean isTransactionEvent() {
        return "transaction.updated".equals(event);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WompiPayoutEventData {

        @JsonProperty("transaction")
        private WompiPayoutTransactionPayload transaction;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WompiPayoutTransactionPayload {

        @JsonProperty("id")
        private String id;

        @JsonProperty("reference")
        private String reference;

        /** Valores: APPROVED, DECLINED, FAILED (además de PENDING, no terminal). */
        @JsonProperty("status")
        private String status;

        @JsonProperty("amountInCents")
        private Long amountInCents;

        @JsonProperty("failureReason")
        private WompiPayoutFailureReason failureReason;

        public boolean isApproved() {
            return "APPROVED".equalsIgnoreCase(status);
        }

        public boolean isTerminal() {
            return "APPROVED".equalsIgnoreCase(status)
                    || "DECLINED".equalsIgnoreCase(status)
                    || "FAILED".equalsIgnoreCase(status);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WompiPayoutFailureReason {

        @JsonProperty("code")
        private String code;

        @JsonProperty("message")
        private String message;
    }
}
