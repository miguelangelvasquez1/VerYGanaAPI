package com.verygana2.dtos.wompi;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

/**
 * Body para POST /payouts (Pagos a Terceros).
 * Reemplaza el flujo de 2 pasos de Kushki (tokenizar + iniciar transferencia):
 * Wompi no requiere tokenización previa, los datos de destino van directo aquí.
 * Referencia: https://docs.wompi.co/docs/colombia/crea-tu-primer-lote/
 */
@Data
@Builder
public class WompiPayoutRequestDTO {

    /** Referencia del lote. Usamos la misma referencia interna del payout. */
    @JsonProperty("reference")
    private String reference;

    /** Cuenta de origen de la dispersión (WompiPayoutConfig.accountId). */
    @JsonProperty("accountId")
    private String accountId;

    /**
     * Tipo de pago. "PAYROLL" es el único valor confirmado en la documentación
     * pública; verificar en sandbox si existe un valor más específico para
     * dispersión a vendedores de marketplace antes de ir a producción.
     */
    @JsonProperty("paymentType")
    @Builder.Default
    private String paymentType = "PAYROLL";

    @JsonProperty("transactions")
    private List<WompiPayoutTransactionDTO> transactions;

    @Data
    @Builder
    public static class WompiPayoutTransactionDTO {

        /** Tipo de documento del titular: CC, CE, NIT, PP. */
        @JsonProperty("legalIdType")
        private String legalIdType;

        @JsonProperty("legalId")
        private String legalId;

        /** bankId (UUID) del catálogo GET /banks — ver WompiPayoutConfig para Nequi/Daviplata. */
        @JsonProperty("bankId")
        private String bankId;

        /** AHORROS o CORRIENTE. */
        @JsonProperty("accountType")
        private String accountType;

        @JsonProperty("accountNumber")
        private String accountNumber;

        @JsonProperty("name")
        private String name;

        @JsonProperty("email")
        private String email;

        /** Monto en centavos de COP. */
        @JsonProperty("amount")
        private Long amount;

        /** Referencia de la transacción individual — "VG-PAYOUT-{payoutId}". */
        @JsonProperty("reference")
        private String reference;
    }
}
