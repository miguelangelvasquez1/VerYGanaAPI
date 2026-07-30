package com.verygana2.services.interfaces.details;


import java.time.ZonedDateTime;

import com.verygana2.dtos.product.responses.CommercialProfileResponseDTO;
import com.verygana2.dtos.user.commercial.CommercialInitialDataResponseDTO;
import com.verygana2.dtos.user.commercial.responses.PayoutReportResponseDTO;
import com.verygana2.dtos.user.commercial.responses.SalesReportResponseDTO;
import com.verygana2.models.userDetails.CommercialDetails;

public interface CommercialDetailsService {

    CommercialInitialDataResponseDTO getCommercialInitialData(Long commercialId);
    CommercialDetails getCommercialById (Long commercialId);
    boolean existsCommercialById(Long commercialId);
    CommercialDetails getCommercialByCompanyName(String companyName);
    PayoutReportResponseDTO getPayoutReport (Long commercialId, Integer year, Integer month);
    /** Reporte de ventas por rango de fechas arbitrario, con top productos vendidos incluido. */
    SalesReportResponseDTO getSalesReport(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate);
    CommercialProfileResponseDTO getCommercialProfile (Long commercialId);
}
