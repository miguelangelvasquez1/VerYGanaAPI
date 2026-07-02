package com.verygana2.dtos.kushki;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Body para POST /payouts/transfer/v1/init
 * Inicia la transferencia bancaria usando el token obtenido previamente.
 */
@Data
@Builder
public class KushkiTransferRequestDTO {

    @JsonProperty("token")
    private String token;          // token obtenido de /tokens

    @JsonProperty("amount")
    private KushkiAmountDTO amount;

    @JsonProperty("merchantTransferReference")
    private String merchantTransferReference; // nuestra referencia interna "VG-PAYOUT-{uuid}"

    @Data
    @Builder
    public static class KushkiAmountDTO {
        @JsonProperty("subtotalIva")
        @Builder.Default
        private Double subtotalIva = 0.0;

        @JsonProperty("subtotalIva0")
        private Double subtotalIva0; // monto sin IVA en unidades (no centavos)

        @JsonProperty("iva")
        @Builder.Default
        private Double iva = 0.0;

        @JsonProperty("currency")
        @Builder.Default
        private String currency = "COP";
    }
}
