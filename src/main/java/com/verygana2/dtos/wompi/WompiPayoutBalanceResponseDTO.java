package com.verygana2.dtos.wompi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Response de GET /accounts (una entrada de la cuenta de dispersión).
 * Formato exacto no confirmado por documentación pública — ajustar campos
 * tras la primera prueba en sandbox.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WompiPayoutBalanceResponseDTO {

    @JsonProperty("id")
    private String id;

    @JsonProperty("balanceInCents")
    private Long balanceInCents;

    @JsonProperty("status")
    private String status;
}
