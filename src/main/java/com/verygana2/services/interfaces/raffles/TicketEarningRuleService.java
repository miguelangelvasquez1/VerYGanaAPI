package com.verygana2.services.interfaces.raffles;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.raffle.requests.CreateTicketEarningRuleRequestDTO;
import com.verygana2.dtos.raffle.requests.UpdateTicketEarningRuleRequestDTO;
import com.verygana2.dtos.raffle.responses.TicketEarningRuleResponseDTO;
import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.TicketEarningRule;

public interface TicketEarningRuleService {

    TicketEarningRule getTicketEarningRuleById (Long ruleId);
    TicketEarningRuleResponseDTO getTicketEarningRuleResponseDTOById(Long ruleId);
    List<TicketEarningRuleResponseDTO> getTicketEarningRulesList(TicketEarningRuleType type, Boolean isActive, Pageable pageable);
    List<TicketEarningRule> getActiveRulesByType(TicketEarningRuleType type);
    EntityCreatedResponseDTO createTicketEarningRule (Long adminId, CreateTicketEarningRuleRequestDTO request);
    EntityUpdatedResponseDTO updateTicketEarningRule (Long adminId, Long ruleId, UpdateTicketEarningRuleRequestDTO request);
    void deleteTicketEarningRule(Long ruleId);
    void activateTicketEarningRule(Long ruleId);
    void deactivateTicketEarningRule (Long ruleId);
}
