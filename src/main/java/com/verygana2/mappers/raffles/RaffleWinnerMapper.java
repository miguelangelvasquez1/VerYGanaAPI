package com.verygana2.mappers.raffles;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.raffle.responses.WinnerSummaryResponseDTO;
import com.verygana2.models.raffles.RaffleWinner;

@Mapper(componentModel = "spring")
public interface RaffleWinnerMapper {
 
    @Mapping(target = "winnerId", source = "id")
    @Mapping(target = "consumerId", source = "winner.id")
    @Mapping(target = "consumerName", source = "winner.userName")
    @Mapping(target = "ticketNumber", source = "winningTicket.ticketNumber")
    @Mapping(target = "prizeTitle", source = "prize.title")
    @Mapping(target = "position", source = "prize.position")
    WinnerSummaryResponseDTO toWinnerSummaryResponseDTO (RaffleWinner winner);

}
