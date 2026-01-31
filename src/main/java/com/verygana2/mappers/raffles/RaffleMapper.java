package com.verygana2.mappers.raffles;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.raffle.requests.CreatePrizeRequestDTO;
import com.verygana2.dtos.raffle.requests.CreateRaffleRequestDTO;
import com.verygana2.dtos.raffle.responses.RaffleResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleStatsResponseDTO;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.Raffle;

@Mapper(componentModel = "spring")
public interface RaffleMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "raffleStatus", ignore = true)
    @Mapping(target = "totalTicketsIssued", ignore = true)
    @Mapping(target = "totalParticipants", ignore = true)
    @Mapping(target = "drawProof", ignore = true)
    @Mapping(target = "tickets", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "maxTicketsFromPlatformGifts", ignore = true)
    @Mapping(target = "currentTicketsFromPurchases", ignore = true)
    @Mapping(target = "currentTicketsFromAds", ignore = true)
    @Mapping(target = "currentTicketsFromGames", ignore = true)
    @Mapping(target = "currentTicketsFromReferrals", ignore = true)
    @Mapping(target = "currentTicketsFromPlatformGifts", ignore = true)
    Raffle toRaffle (CreateRaffleRequestDTO request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "raffle", ignore = true)
    @Mapping(target = "claimedCount", ignore = true)
    @Mapping(target = "prizeStatus", ignore = true)
    @Mapping(target = "winner", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Prize toPrize (CreatePrizeRequestDTO request);

    RaffleResponseDTO toRaffleResponseDTO (Raffle raffle);

    @Mapping(target = "ticketsBySource", ignore = true)
    RaffleStatsResponseDTO toRaffleStatsResponseDTO (Raffle raffle);
}
