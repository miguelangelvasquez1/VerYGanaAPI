package com.verygana2.services.interfaces.details;

import com.verygana2.dtos.user.commercial.CommercialInitialDataResponseDTO;

public interface CommercialDetailsService {
    
    CommercialInitialDataResponseDTO getCommercialInitialData(Long consumerId);
}
