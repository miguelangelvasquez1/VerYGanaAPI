package com.verygana2.dtos.wompi;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Response de POST /payouts y GET /payouts/{id}.
 * Formato exacto no confirmado por documentación pública (solo el request
 * está documentado) — ajustar campos tras la primera prueba en sandbox.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WompiPayoutResponseDTO {

    @JsonProperty("id")
    private String id;

    @JsonProperty("reference")
    private String reference;

    /** PENDING, APPROVED, DECLINED, ERROR (mismos valores que WompiTransactionStatus). */
    @JsonProperty("status")
    private String status;

    @JsonProperty("transactions")
    private List<WompiPayoutTransactionResultDTO> transactions;

    public boolean isAccepted() {
        return status != null && !"ERROR".equalsIgnoreCase(status) && !"DECLINED".equalsIgnoreCase(status);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WompiPayoutTransactionResultDTO {

        @JsonProperty("id")
        private String id;

        @JsonProperty("reference")
        private String reference;

        @JsonProperty("status")
        private String status;

        @JsonProperty("amount")
        private Long amount;
    }
}
