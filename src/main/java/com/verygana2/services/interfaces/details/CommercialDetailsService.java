package com.verygana2.services.interfaces.details;


import com.verygana2.dtos.product.responses.CommercialProfileResponseDTO;
import com.verygana2.dtos.user.commercial.CommercialInitialDataResponseDTO;
import com.verygana2.dtos.user.commercial.responses.MonthlyReportResponseDTO;
import com.verygana2.models.userDetails.CommercialDetails;

public interface CommercialDetailsService {
    
    CommercialInitialDataResponseDTO getCommercialInitialData(Long commercialId);
    CommercialDetails getCommercialById (Long commercialId);
    boolean existsCommercialById(Long commercialId);
    CommercialDetails getCommercialByCompanyName(String companyName);
    MonthlyReportResponseDTO getMonthlyReport (Long commercialId, Integer year, Integer month);
    CommercialProfileResponseDTO getCommercialProfile (Long commercialId);
}
