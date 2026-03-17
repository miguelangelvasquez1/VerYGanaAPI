package com.verygana2.mappers.raffles;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.raffle.responses.RaffleResultResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleSummaryResultResponseDTO;
import com.verygana2.dtos.raffle.responses.WinnerDetailResponseDTO;
import com.verygana2.models.raffles.RaffleResult;
import com.verygana2.models.raffles.RaffleWinner;

@Mapper(componentModel = "spring")
public interface RaffleResultMapper {
    @Mapping(target = "raffleId", source = "raffle.id")
    @Mapping(target = "raffleTitle", source = "raffle.title")
    @Mapping(target = "raffleType", source = "raffle.raffleType")
    RaffleSummaryResultResponseDTO toRaffleSummaryResultResponseDTO (RaffleResult raffleResult);

    @Mapping(target = "raffleId", source = "raffle.id")
    @Mapping(target = "raffleTitle", source = "raffle.title")
    @Mapping(target = "raffleType", source = "raffle.raffleType")
    @Mapping(target = "totalParticipants", source = "raffle.totalParticipants")
    @Mapping(target = "totalTicketsIssued", source = "raffle.totalTicketsIssued")
    @Mapping(target = "winners", source = "winners")
    RaffleResultResponseDTO toRaffleResultDTO (RaffleResult raffleResult);
    
    @Mapping(target = "userName", expression = "java(toDisplayName(winner.getWinner()))")
    @Mapping(target = "ticketNumber", source = "winningTicket.ticketNumber")
    @Mapping(target = "position", source = "prize.position")
    @Mapping(target = "prizeTitle", source = "prize.title")
    @Mapping(target = "prizeImageUrl", expression =  "java(getPrizeImageUrl(prize.imageAsset.objectKey))")
    @Mapping(target = "prizeType", source = "prize.prizeType")
    @Mapping(target = "prizeValue", source = "prize.value")
    WinnerDetailResponseDTO toWinnerDetailDTO(RaffleWinner winner);

    default String getPrizeImageUrl (String objectKey) {
        return "https://" + objectKey;
    }
}
