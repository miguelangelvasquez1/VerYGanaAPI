package com.verygana2.dtos.wompi;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Response de GET /banks. Envelope asumido consistente con /accounts y
 * /payouts (status/code/data) — no hay ejemplo público confirmado del
 * cuerpo exacto de cada entrada; se leen "id" y "name" y se ignora
 * cualquier otro campo. Ajustar si el sandbox real usa otros nombres.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WompiPayoutBankResponseDTO {

    @JsonProperty("status")
    private Integer status;

    @JsonProperty("code")
    private String code;

    @JsonProperty("data")
    private List<Bank> data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Bank {

        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;
    }
}
