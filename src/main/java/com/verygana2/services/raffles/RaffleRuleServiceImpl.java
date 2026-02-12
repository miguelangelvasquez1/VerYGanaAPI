package com.verygana2.services.raffles;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;

import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.RaffleRule;
import com.verygana2.repositories.raffles.RaffleRuleRespository;
import com.verygana2.services.interfaces.raffles.RaffleRuleService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RaffleRuleServiceImpl implements RaffleRuleService {

    private final RaffleRuleRespository raffleRuleRespository;

    @Override
    public RaffleRule getByRaffleIdAndRuleType(Long raffleId, TicketEarningRuleType ruleType) {
        if (raffleId == null || raffleId <= 0) {
            throw new IllegalArgumentException("raffle id must be positive");
        }

        return raffleRuleRespository.findByRaffleIdAndRuleType(raffleId, ruleType)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Raffle rule with raffle id: " + raffleId + " and ruleType: " + ruleType + " not found ",
                        RaffleRule.class));
    }

}
