package com.verygana2.dtos.seller.responses;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MonthlyReportResponseDTO {
    private Long sellerId;
    private Integer month;
    private BigDecimal totalSalesAmount;
    private BigDecimal earnings;
    private BigDecimal totalPlatformCommissionsAmount;
    private Integer year;
}
