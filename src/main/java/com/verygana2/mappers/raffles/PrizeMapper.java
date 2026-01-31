package com.verygana2.mappers.raffles;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.raffle.requests.CreatePrizeRequestDTO;
import com.verygana2.models.raffles.Prize;

@Mapper(componentModel = "spring")
public interface PrizeMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "raffle", ignore = true)
    @Mapping(target = "claimedCount", ignore = true)
    @Mapping(target = "prizeStatus", ignore = true)
    @Mapping(target = "winner", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Prize toPrize (CreatePrizeRequestDTO request);
}
