package com.verygana2.dtos.wallet.responses;

import java.time.ZonedDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DepositResponseDTO {

    public enum DepositType {
        SUBSCRIPTION,
        INVESTMENT
    }

    private DepositType type;
    private String description;
    private Long amountCents;
    private String referenceId;
    private ZonedDateTime date;
    private String status;
}
