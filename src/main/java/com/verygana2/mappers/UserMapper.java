package com.verygana2.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.user.AdvertiserRegisterDTO;
import com.verygana2.dtos.user.ConsumerRegisterDTO;
import com.verygana2.dtos.user.SellerRegisterDTO;
import com.verygana2.models.User;
import com.verygana2.models.userDetails.AdvertiserDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.models.userDetails.SellerDetails;

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
    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "userName", ignore = true)
    @Mapping(target = "notifications", ignore = true)
    ConsumerDetails toConsumerDetails(ConsumerRegisterDTO dto);

    // ---- ADVERTISER ----
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", constant = "ADVERTISER")
    @Mapping(target = "userDetails", ignore = true)
    @Mapping(target = "userState", constant = "ACTIVE")
    @Mapping(target = "registeredDate", expression = "java(java.time.ZonedDateTime.now())")
    @Mapping(target = "verification", ignore = true)
    @Mapping(target = "wallet", ignore = true)
    User toUser(AdvertiserRegisterDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "notifications", ignore = true)
    AdvertiserDetails toAdvertiserDetails(AdvertiserRegisterDTO dto);

    // ---- SELLER ----
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", constant = "SELLER")
    @Mapping(target = "userDetails", ignore = true)
    @Mapping(target = "userState", constant = "ACTIVE")
    @Mapping(target = "registeredDate", expression = "java(java.time.ZonedDateTime.now())")
    @Mapping(target = "verification", ignore = true)
    @Mapping(target = "wallet", ignore = true)
    User toUser(SellerRegisterDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "notifications", ignore = true)
    SellerDetails toSellerDetails(SellerRegisterDTO dto);
}