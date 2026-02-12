package com.verygana2.services.interfaces.raffles;

import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.RaffleRule;

public interface RaffleRuleService {
    RaffleRule getByRaffleIdAndRuleType (Long raffleId, TicketEarningRuleType ruleType);
}
