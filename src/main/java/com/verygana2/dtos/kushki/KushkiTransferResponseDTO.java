package com.verygana2.dtos.kushki;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Respuesta de POST /payouts/transfer/v1/init
 */
@Data
public class KushkiTransferResponseDTO {

    @JsonProperty("transferId")
    private String transferId;     // ID que Kushki asigna a la transferencia

    @JsonProperty("code")
    private String code;           // "000" = éxito

    @JsonProperty("message")
    private String message;

    @JsonProperty("status")
    private String status;         // "PENDING", "APPROVED", etc.

    public boolean isSuccess() {
        return "000".equals(code);
    }
}
