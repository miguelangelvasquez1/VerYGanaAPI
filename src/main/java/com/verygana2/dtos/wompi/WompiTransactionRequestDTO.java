package com.verygana2.dtos.wompi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Cuerpo del request para crear una transacción en Wompi.
 * Referencia: https://docs.wompi.co/docs/colombia/transacciones
 */
@Data
@Builder
public class WompiTransactionRequestDTO {

    /**
     * Monto en centavos de COP. Wompi no acepta decimales.
     * Ej: $15.000 COP = 1_500_000 centavos.
     */
    @JsonProperty("amount_in_cents")
    private Long amountInCents;

    /** Siempre "COP" para Colombia. */
    @JsonProperty("currency")
    @Builder.Default
    private String currency = "COP";

    /**
     * Referencia única de tu sistema. Wompi la devuelve en el webhook
     * para que puedas identificar qué Copayment o pago corresponde.
     * Máximo 50 caracteres. Recomendado: "VG-{tipo}-{uuid}".
     */
    @JsonProperty("reference")
    private String reference;

    /**
     * Hash de integridad SHA-256.
     * Generado por WompiClient.generateIntegrityHash().
     */
    @JsonProperty("signature")
    private String integritySignature;

    /** Email del cliente. Wompi lo usa para enviar recibo. */
    @JsonProperty("customer_email")
    private String customerEmail;

    /**
     * Método de pago. Ejemplos: "CARD", "NEQUI", "PSE", "BANCOLOMBIA_TRANSFER".
     * Se envuelve en un objeto según el tipo — ver WompiPaymentMethodData.
     */
    @JsonProperty("payment_method")
    private WompiPaymentMethodData paymentMethod;

    /** Token de la public key. Requerido por Wompi. */
    @JsonProperty("public_key")
    private String publicKey;

    @Data
    @Builder
    public static class WompiPaymentMethodData {

        /** Tipo de método: "CARD", "NEQUI", "PSE", "BANCOLOMBIA_TRANSFER" */
        @JsonProperty("type")
        private String type;

        /**
         * Token de la tarjeta (para pagos con tarjeta).
         * Se obtiene tokenizando la tarjeta desde el frontend con la public key.
         */
        @JsonProperty("token")
        private String token;

        /** Número de cuotas (solo para tarjetas). */
        @JsonProperty("installments")
        private Integer installments;

        /** Número de celular (solo para Nequi). */
        @JsonProperty("phone_number")
        private String phoneNumber;
    }
}