package com.verygana2.mappers.raffles;

import java.time.ZoneOffset;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.verygana2.dtos.raffle.requests.CreatePrizeRequestDTO;
import com.verygana2.dtos.raffle.requests.CreateRaffleRequestDTO;
import com.verygana2.dtos.raffle.responses.PrizeResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleRuleResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleStatsResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleSummaryResponseDTO;
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
    @Mapping(target = "issuedTickets", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "raffleRules", ignore = true)
    @Mapping(target = "modifiedBy", ignore = true)
    @Mapping(target = "imageAsset", ignore = true)
    @Mapping(target = "prizes", ignore = true)
    Raffle toRaffle(CreateRaffleRequestDTO request);

    @AfterMapping
    default void normalizeDatesToUTC(@MappingTarget Raffle raffle) {
        if (raffle.getStartDate() != null) {
            raffle.setStartDate(raffle.getStartDate().withZoneSameInstant(ZoneOffset.UTC));
        }
        if (raffle.getEndDate() != null) {
            raffle.setEndDate(raffle.getEndDate().withZoneSameInstant(ZoneOffset.UTC));
        }
        if (raffle.getDrawDate() != null) {
            raffle.setDrawDate(raffle.getDrawDate().withZoneSameInstant(ZoneOffset.UTC));
        }
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "raffle", ignore = true)
    @Mapping(target = "claimedCount", ignore = true)
    @Mapping(target = "prizeStatus", ignore = true)
    @Mapping(target = "winner", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "imageAsset", ignore = true)
    Prize toPrize(CreatePrizeRequestDTO request);

    @Mapping(target = "prizeCount", expression = "java(getPrizeCount(raffle))")
    @Mapping(target = "imageUrl", source = "imageAsset.objectKey")
    RaffleSummaryResponseDTO toRaffleSummaryResponseDTO(Raffle raffle);

    @Mapping(target = "rules", source = "raffleRules")
    @Mapping(target = "imageUrl", ignore = true)
    RaffleResponseDTO toRaffleResponseDTO(Raffle raffle);

    @Mapping(target = "imageUrl", source = "imageAsset.objectKey")
    PrizeResponseDTO toPrizeResponseDTO(Prize prize);

    @Mapping(target = "raffleId", source = "id")
    @Mapping(target = "ticketEarningRuleResponseDTO", source = "ticketEarningRule")
    RaffleRuleResponseDTO toRaffleResponseDTO(RaffleRule raffleRule);

    TicketEarningRuleResponseDTO toRuleResponseDTO(TicketEarningRule ticketEarningRule);

    @Mapping(target = "ticketsBySource", ignore = true)
    @Mapping(target = "maxTicketsFromPurchases", ignore = true)
    @Mapping(target = "maxTicketsFromAds", ignore = true)
    @Mapping(target = "maxTicketsFromGames", ignore = true)
    @Mapping(target = "maxTicketsFromReferrals", ignore = true)
    RaffleStatsResponseDTO toRaffleStatsResponseDTO(Raffle raffle);

    default Long getPrizeCount(Raffle raffle) {
        return raffle.getPrizes() != null ? (long) raffle.getPrizes().size() : 0;
    }
}
