package com.verygana2.dtos.wompi;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Response de GET /accounts. Sigue el mismo envelope que POST /payouts
 * (status/code/message/data) — no confirmado con un ejemplo JSON público
 * para este endpoint específico, pero consistente con el resto de la API
 * de Pagos a Terceros. Ajustar si sandbox devuelve un array plano.
 *
 * {
 *   "status": 200,
 *   "code": "OK",
 *   "data": [ { "id": "...", "balanceInCents": 2500000, "status": "ACTIVE" } ]
 * }
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WompiPayoutBalanceResponseDTO {

    @JsonProperty("status")
    private Integer status;

    @JsonProperty("code")
    private String code;

    @JsonProperty("data")
    private List<Account> data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Account {

        @JsonProperty("id")
        private String id;

        @JsonProperty("balanceInCents")
        private Long balanceInCents;

        /** IN_REVIEW, ACTIVE o INACTIVE. */
        @JsonProperty("status")
        private String status;
    }
}
