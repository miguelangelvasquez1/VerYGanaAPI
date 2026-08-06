package com.verygana2.services.interfaces.details;


import java.time.ZonedDateTime;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.product.responses.CommercialProfileResponseDTO;
import com.verygana2.dtos.user.commercial.CommercialInitialDataResponseDTO;
import com.verygana2.dtos.user.commercial.responses.DailySaleResponseDTO;
import com.verygana2.dtos.user.commercial.responses.PayoutReportResponseDTO;
import com.verygana2.dtos.user.commercial.responses.SalesReportResponseDTO;
import com.verygana2.models.userDetails.CommercialDetails;

public interface CommercialDetailsService {

    CommercialInitialDataResponseDTO getCommercialInitialData(Long commercialId);
    CommercialDetails getCommercialById (Long commercialId);
    boolean existsCommercialById(Long commercialId);
    CommercialDetails getCommercialByCompanyName(String companyName);
    PayoutReportResponseDTO getPayoutReport (Long commercialId, Integer year, Integer month);
    /** Los 12 meses del año dado, para graficar el payout mensual (ej. bar chart). */
    PayoutReportResponseDTO[] getPayoutReports (Long commercialId, Integer year);
    /** Los 12 meses del año dado, para graficar las ventas mensuales (ej. bar chart). */
    SalesReportResponseDTO[] getSalesReports(Long commercialId, Integer year);
    SalesReportResponseDTO getSalesReport(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate);
    Integer getSalesCount (Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate);
    /** Listado transaccional día a día (ventas individuales) dentro del rango. */
    PagedResponse<DailySaleResponseDTO> getDailySales(
            Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable);
    CommercialProfileResponseDTO getCommercialProfile (Long commercialId);
}
