package com.verygana2.services.interfaces;

import java.math.BigDecimal;

public interface ReferralPromotionService {
    void addPointsForReferral(Long userId, BigDecimal amount, Long userReferiedId);
}
