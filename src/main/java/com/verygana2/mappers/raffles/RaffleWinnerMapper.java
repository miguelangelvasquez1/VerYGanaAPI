package com.verygana2.mappers.raffles;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.raffle.responses.WinnerSummaryResponseDTO;
import com.verygana2.models.raffles.RaffleWinner;

@Mapper(componentModel = "spring")
public interface RaffleWinnerMapper {
 
    @Mapping(target = "userName", source = "winner.userName")
    @Mapping(target = "raffleTitle", source = "raffleResult.raffle.title")
    @Mapping(target = "prizeTitle", source = "prize.title")
    @Mapping(target = "prizeValue", source = "prize.value")
    @Mapping(target = "position", source = "prize.position")
    WinnerSummaryResponseDTO toWinnerSummaryResponseDTO (RaffleWinner winner);
}
