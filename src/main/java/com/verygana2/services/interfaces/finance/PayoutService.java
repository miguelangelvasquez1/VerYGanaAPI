package com.verygana2.services.interfaces.finance;

import java.math.BigDecimal;

public interface PayoutService {
    BigDecimal getCommercialEarningsForPeriod (Long commercialId, Integer year, Integer month);
}
