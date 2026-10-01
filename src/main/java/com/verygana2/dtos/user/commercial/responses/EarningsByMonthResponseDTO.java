package com.verygana2.dtos.user.commercial.responses;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EarningsByMonthResponseDTO {
    UUID sellerPublicId;
    Integer year;
    Integer month;
    BigDecimal earnings;
}
