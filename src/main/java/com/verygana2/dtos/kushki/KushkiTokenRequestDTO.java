package com.verygana2.dtos.kushki;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Body para POST /payouts/transfer/v1/tokens
 * Tokeniza la cuenta destino antes de iniciar la transferencia.
 */
@Data
@Builder
public class KushkiTokenRequestDTO {

    @JsonProperty("documentType")
    private String documentType;   // CC, CE, NIT, PP

    @JsonProperty("documentNumber")
    private String documentNumber;

    @JsonProperty("name")
    private String name;           // nombre del titular

    @JsonProperty("bankCode")
    private String bankCode;       // código ACH del banco destino

    @JsonProperty("bankAccountType")
    private String bankAccountType; // SAVINGS | CHECKING

    @JsonProperty("bankAccountNumber")
    private String bankAccountNumber;

    @JsonProperty("currency")
    @Builder.Default
    private String currency = "COP";
}
