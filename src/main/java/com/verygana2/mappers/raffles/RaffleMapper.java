package com.verygana2.mappers.raffles;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.raffle.requests.CreatePrizeRequestDTO;
import com.verygana2.dtos.raffle.requests.CreateRaffleRequestDTO;
import com.verygana2.dtos.raffle.responses.RaffleResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleRuleResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleStatsResponseDTO;
import com.verygana2.dtos.raffle.responses.TicketEarningRuleResponseDTO;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleRule;
import com.verygana2.models.raffles.TicketEarningRule;

@Mapper(componentModel = "spring")
public interface RaffleMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "raffleStatus", ignore = true)
    @Mapping(target = "totalTicketsIssued", ignore = true)
    @Mapping(target = "totalParticipants", ignore = true)
    @Mapping(target = "drawProof", ignore = true)
    @Mapping(target = "issuedTickets", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "raffleRules", ignore = true)
    Raffle toRaffle (CreateRaffleRequestDTO request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "raffle", ignore = true)
    @Mapping(target = "claimedCount", ignore = true)
    @Mapping(target = "prizeStatus", ignore = true)
    @Mapping(target = "winner", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Prize toPrize (CreatePrizeRequestDTO request);

    @Mapping(target = "rules", source = "raffleRules")
    RaffleResponseDTO toRaffleResponseDTO (Raffle raffle);

    @Mapping(target = "raffleId", source = "id")
    @Mapping(target = "ticketEarningRuleResponseDTO", source = "ticketEarningRule")
    RaffleRuleResponseDTO toRaffleResponseDTO (RaffleRule raffleRule);

    TicketEarningRuleResponseDTO toRuleResponseDTO (TicketEarningRule ticketEarningRule);

    @Mapping(target = "ticketsBySource", ignore = true)
    RaffleStatsResponseDTO toRaffleStatsResponseDTO (Raffle raffle);
}
