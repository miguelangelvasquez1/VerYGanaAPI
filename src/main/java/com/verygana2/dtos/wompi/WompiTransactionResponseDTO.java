package com.verygana2.dtos.wompi;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Respuesta de Wompi al crear o consultar una transacción.
 * Wompi envuelve el objeto en {"data": {...}}.
 *
 * @JsonIgnoreProperties(ignoreUnknown = true) porque Wompi puede agregar
 * campos nuevos en su API sin aviso previo — no queremos que la deserialización
 * falle por eso.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WompiTransactionResponseDTO {

    @JsonProperty("data")
    private WompiTransactionData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WompiTransactionData {

        /** ID único de la transacción en Wompi. Ej: "1234-1668097749-23456" */
        @JsonProperty("id")
        private String id;

        /**
         * Estado de la transacción.
         * Valores posibles: PENDING, APPROVED, DECLINED, VOIDED, ERROR
         */
        @JsonProperty("status")
        private String status;

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

        /**
         * Payload completo del método de pago.
         * Se guarda en WompiTransaction.metadata para auditoría.
         */
        @JsonProperty("payment_method")
        private Map<String, Object> paymentMethod;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("finalized_at")
        private String finalizedAt;

        /** Mensaje de error si status = DECLINED o ERROR */
        @JsonProperty("status_message")
        private String statusMessage;

        /** true si el estado es terminal (no va a cambiar más) */
        public boolean isTerminal() {
            return "APPROVED".equals(status)
                    || "DECLINED".equals(status)
                    || "VOIDED".equals(status)
                    || "ERROR".equals(status);
        }

        public boolean isApproved() {
            return "APPROVED".equals(status);
        }
    }
}
