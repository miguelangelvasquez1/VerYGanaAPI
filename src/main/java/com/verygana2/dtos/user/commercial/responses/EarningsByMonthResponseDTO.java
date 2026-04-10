package com.verygana2.dtos.user.commercial.responses;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EarningsByMonthResponseDTO {
    Long sellerId;
    Integer year;
    Integer month;
    BigDecimal earnings;
}
