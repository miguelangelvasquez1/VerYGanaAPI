package com.verygana2.dtos.raffle.responses;

import java.math.BigDecimal;

import com.verygana2.models.enums.raffles.PrizeType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WinnerDetailResponseDTO {

    private String userName;
    private String ticketNumber;
    private String prizeTitle;
    private String prizeImageUrl;
    private PrizeType prizeType;
    private BigDecimal prizeValue;
    private Integer position;
}
