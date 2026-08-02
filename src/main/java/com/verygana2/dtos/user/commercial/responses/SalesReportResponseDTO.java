package com.verygana2.dtos.user.commercial.responses;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.dtos.product.responses.FeaturedProductResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reporte de ventas por rango de fechas arbitrario (a diferencia de
 * {@link MonthlyReportResponseDTO}, que solo acepta año/mes completos).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SalesReportResponseDTO {
    private Long commercialId;
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private BigDecimal totalSalesAmount;
    private Integer totalSalesCount;
    private BigDecimal totalPlatformCommissionsAmount;
    private List<FeaturedProductResponseDTO> topSellingProducts;
}
