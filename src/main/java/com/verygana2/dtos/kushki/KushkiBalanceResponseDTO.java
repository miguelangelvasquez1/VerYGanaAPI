package com.verygana2.dtos.kushki;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Respuesta de GET /payouts/balance/v1
 */
@Data
public class KushkiBalanceResponseDTO {

    @JsonProperty("availableBalance")
    private Double availableBalance; // en unidades COP (no centavos)

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;

    public long getAvailableBalanceCents() {
        if (availableBalance == null) return 0L;
        return Math.round(availableBalance * 100);
    }
}
