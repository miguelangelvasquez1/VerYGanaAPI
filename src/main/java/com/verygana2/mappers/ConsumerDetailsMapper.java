package com.verygana2.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.verygana2.dtos.user.consumer.requests.ConsumerUpdateProfileRequestDTO;
import com.verygana2.dtos.user.consumer.responses.ConsumerInitialDataResponseDTO;
import com.verygana2.dtos.user.consumer.responses.ConsumerProfileResponseDTO;
import com.verygana2.models.userDetails.ConsumerDetails;

@Mapper(componentModel = "spring")
public interface ConsumerDetailsMapper {
    
    @Mapping(target = "walletAvailableBalance", ignore = true)
    ConsumerInitialDataResponseDTO toConsumerInitialDataResponseDTO (ConsumerDetails consumer);
    
    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "email", source = "consumer.user.email")
    @Mapping(target = "phoneNumber", source = "consumer.user.phoneNumber")
    @Mapping(target = "role", source = "consumer.user.role")
    @Mapping(target = "userState", source = "consumer.user.userState")
    ConsumerProfileResponseDTO toConsumerProfileResponseDTO (ConsumerDetails consumer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    @Mapping(target = "adsWatched", ignore = true)
    @Mapping(target = "totalWithdraws", ignore = true)
    @Mapping(target = "dailyAdCount", ignore = true)
    @Mapping(target = "referralCode", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "favoriteProducts", ignore = true)
    @Mapping(target = "raffleTickets", ignore = true)
    @Mapping(target = "user.email", source = "email")
    @Mapping(target = "user.phoneNumber", source = "phoneNumber")
    void updateConsumerFromDto(ConsumerUpdateProfileRequestDTO dto, @MappingTarget ConsumerDetails entity);

    
}
