package com.verygana2.mappers.raffles;

import org.mapstruct.Mapper;

import com.verygana2.dtos.raffle.responses.RuleResponseDTO;
import com.verygana2.models.raffles.TicketEarningRule;

@Mapper(componentModel = "spring")
public interface TicketEarningRuleMapper {
    
    RuleResponseDTO toRuleResponseDTO (TicketEarningRule ticketEarningRule);
}
