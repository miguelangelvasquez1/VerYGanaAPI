package com.verygana2.services.interfaces.raffles;

import java.math.BigDecimal;

import com.verygana2.dtos.raffle.responses.TicketEarningResult;

public interface TicketDeliveryService {
    
    TicketEarningResult processTicketEarningForPurchase(Long consumerId, Long purchaseId, BigDecimal purchaseAmount);
    void processTicketEarningForAds(Long consumerId, Long adSessionId, Integer adsWatchedCount);
    void processTicketEarningForGames(Long consumerId, Long gameId, Integer score);
    void processTicketEarningForReferral(Long consumerId, Long referralId);
}
