package com.verygana2.dtos.kushki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Payload del webhook que Kushki envía cuando una transferencia cambia de estado.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KushkiWebhookEvent {

    @JsonProperty("transferId")
    private String transferId;

    @JsonProperty("merchantTransferReference")
    private String merchantTransferReference; // nuestra referencia "VG-PAYOUT-{uuid}"

    @JsonProperty("status")
    private String status;  // "APPROVED", "DECLINED", "FAILED"

    @JsonProperty("amount")
    private Double amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;

    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(status);
    }

    public boolean isTerminal() {
        return "APPROVED".equalsIgnoreCase(status)
                || "DECLINED".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status);
    }
}
