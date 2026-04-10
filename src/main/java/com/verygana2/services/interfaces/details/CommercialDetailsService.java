package com.verygana2.services.interfaces.details;

import com.verygana2.dtos.user.commercial.CommercialInitialDataResponseDTO;
import com.verygana2.dtos.user.commercial.responses.MonthlyReportResponseDTO;
import com.verygana2.models.userDetails.CommercialDetails;

public interface CommercialDetailsService {
    
    CommercialInitialDataResponseDTO getCommercialInitialData(Long consumerId);
    CommercialDetails getCommercialById (Long commercialId);
    boolean existsCommercialById(Long commercialId);
    CommercialDetails getCommercialByCompanyName(String companyName);
    void getCommercialStats(Long commercialId); // pending
    MonthlyReportResponseDTO getMonthlyReport (Long commercialId, Integer year, Integer month);
}
