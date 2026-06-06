package com.verygana2.services.interfaces.raffles;

import com.verygana2.dtos.raffle.responses.TicketEarningResult;

public interface TicketDeliveryService {
    
    TicketEarningResult processTicketEarningForPurchase(Long consumerId, Long purchaseId, Long purchaseAmountCents);
    void processTicketEarningForDailyLogin(Long consumerId);
    void processTicketEarningForReferral(Long consumerId, Long referralId);
}
