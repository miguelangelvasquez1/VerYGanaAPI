package com.verygana2.dtos.raffle.responses;

import java.math.BigDecimal;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WinnerSummaryResponseDTO {
    private String userName;
    private String raffleTitle;
    private String prizeTitle;
    private BigDecimal prizeValue;
    private Integer position;
}
