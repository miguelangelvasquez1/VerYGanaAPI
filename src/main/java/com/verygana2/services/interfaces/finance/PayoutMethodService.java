package com.verygana2.services.interfaces.finance;


import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.models.finance.PayoutMethod;

public interface PayoutMethodService {
    PagedResponse<PayoutMethod> getByCommercialId (Long commercialId, Pageable pageable);
}
