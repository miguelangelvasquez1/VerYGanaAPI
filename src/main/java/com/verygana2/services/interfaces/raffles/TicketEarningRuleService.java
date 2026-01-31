package com.verygana2.services.interfaces.raffles;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.raffle.requests.CreateRuleRequestDTO;
import com.verygana2.dtos.raffle.requests.UpdateRuleRequestDTO;
import com.verygana2.dtos.raffle.responses.RuleResponseDTO;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.enums.raffles.RuleType;
import com.verygana2.models.products.Purchase;
import com.verygana2.models.raffles.TicketEarningRule;

public interface TicketEarningRuleService {

    EntityCreatedResponseDTO createTicketEarningRule (CreateRuleRequestDTO request);

    EntityUpdatedResponseDTO updateTicketEarningRule (Long rule ,UpdateRuleRequestDTO request);

    void deleteTicketEarningRule (Long ruleId);

    List<RuleResponseDTO> getTicketEarningRulesList (RuleType type, boolean isActive, Pageable Pageable);

    RuleResponseDTO getTicketEarningRuleResponseDTOById (Long ruleId);

    TicketEarningRule getTicketEarningRuleById (Long ruleId);

    void activateTicketEarningRule (Long ruleId);

    void deactivateTicketEarningRule (Long ruleId);

    void onPurchaseCompleted(Purchase Purchase);

    void onGameAchievement(Long consumerId, String achievementType, Integer value);

    void onReferralCompleted(Long referrerId, Long referredId, Long purchaseId);

    void onAdWatched(Long consumerId, Long adId);

    boolean evaluateRule(TicketEarningRule rule,
            Long consumerId,
            RaffleTicketSource sourceType,
            Object context);

    Integer calculateTicketsForRule(
        TicketEarningRule rule, 
        Object context
    );       

    void applyRule(TicketEarningRule rule, Long consumerId, String context);

    boolean checkDailyLimit(Long consumerId, Long ruleId);

    boolean checkTotalLimit(Long consumerId, Long ruleId);

    boolean checkRaffleEligibility(Long consumerId, RaffleType type);

    List<TicketEarningRule> getActiveRulesByType(RaffleTicketSource type);
}
