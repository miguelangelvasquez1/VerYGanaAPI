package com.verygana2.mappers.raffles;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.raffle.requests.CreatePrizeRequestDTO;
import com.verygana2.dtos.raffle.responses.PrizeResponseDTO;
import com.verygana2.models.raffles.Prize;
import com.verygana2.storage.service.PublicUrlBuilder;

@Mapper(componentModel = "spring", uses = PublicUrlBuilder.class)
public interface PrizeMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "raffle", ignore = true)
    @Mapping(target = "claimedCount", ignore = true)
    @Mapping(target = "prizeStatus", ignore = true)
    @Mapping(target = "winner", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "imageAsset", ignore = true)
    Prize toPrize (CreatePrizeRequestDTO request);

    @Mapping(target = "imageUrl", source = "imageAsset.objectKey", qualifiedByName = "publicUrl")
    PrizeResponseDTO toPrizeResponseDTO (Prize prize);
}
