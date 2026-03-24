package com.verygana2.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.user.CommercialRegisterDTO;
import com.verygana2.dtos.user.ConsumerRegisterDTO;
import com.verygana2.dtos.user.commercial.CommercialInitialDataResponseDTO;
import com.verygana2.models.User;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;

@Mapper(componentModel = "spring")
public interface UserMapper {

    // ---- CONSUMER ----
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", constant = "CONSUMER")
    @Mapping(target = "userDetails", ignore = true)
    @Mapping(target = "userState", constant = "ACTIVE")
    @Mapping(target = "registeredDate", expression = "java(java.time.ZonedDateTime.now())")
    @Mapping(target = "verification", ignore = true)
    @Mapping(target = "wallet", ignore = true)
    User toUser(ConsumerRegisterDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "adsWatched", ignore = true)
    @Mapping(target = "dailyAdCount", ignore = true)
    @Mapping(target = "referralCode", ignore = true)
    @Mapping(target = "totalWithdraws", ignore = true)
    @Mapping(target = "favoriteProducts", ignore = true)
    @Mapping(target = "raffleTickets", ignore = true)
    @Mapping(target = "age", ignore = true)
    @Mapping(target = "gender", ignore = true)
    @Mapping(target = "userHash", ignore = true)
    @Mapping(target = "hasPet", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "userName", ignore = true)
    @Mapping(target = "notifications", ignore = true)
    @Mapping(target = "municipality", ignore = true)
    @Mapping(target = "referrals", ignore = true)
    @Mapping(target = "referredBy", ignore = true)
    ConsumerDetails toConsumerDetails(ConsumerRegisterDTO dto);

    // ---- COMMERCIAL ----
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", constant = "COMMERCIAL")
    @Mapping(target = "userDetails", ignore = true)
    @Mapping(target = "userState", constant = "ACTIVE")
    @Mapping(target = "registeredDate", expression = "java(java.time.ZonedDateTime.now())")
    @Mapping(target = "verification", ignore = true)
    @Mapping(target = "wallet", ignore = true)
    User toUser(CommercialRegisterDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "notifications", ignore = true)
    CommercialDetails toCommercialDetails(CommercialRegisterDTO dto);

    @Mapping(target = "email", source = "details.user.email")
    CommercialInitialDataResponseDTO toCommercialInitialDataResponseDTO(CommercialDetails details);
}