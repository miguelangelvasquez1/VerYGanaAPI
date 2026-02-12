package com.verygana2.mappers.raffles;

import org.mapstruct.Mapper;

import com.verygana2.dtos.raffle.responses.TicketEarningRuleResponseDTO;
import com.verygana2.models.raffles.TicketEarningRule;

@Mapper(componentModel = "spring")
public interface TicketEarningRuleMapper {
    
    TicketEarningRuleResponseDTO toRuleResponseDTO (TicketEarningRule ticketEarningRule);
}
