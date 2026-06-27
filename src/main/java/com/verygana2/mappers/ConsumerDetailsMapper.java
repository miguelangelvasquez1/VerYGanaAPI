package com.verygana2.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Value;

import com.verygana2.dtos.user.consumer.requests.ConsumerUpdateProfileRequestDTO;
import com.verygana2.dtos.user.consumer.responses.ConsumerInitialDataResponseDTO;
import com.verygana2.dtos.user.consumer.responses.ConsumerProfileResponseDTO;
import com.verygana2.models.userDetails.ConsumerDetails;

@Mapper(componentModel = "spring")
public abstract class ConsumerDetailsMapper {

    @Value("${financial.key-value-cents:1000}")
    protected long keyValueCents;

    @Named("centsToKeys")
    protected Long centsToKeys(Long cents) {
        if (cents == null) return 0L;
        return cents / keyValueCents;
    }

    @Mapping(target = "totalAvailableKeys", source = "keyWallet.availableKeys", qualifiedByName = "centsToKeys")
    @Mapping(target = "purchaseKeys", source = "keyWallet.purchaseKeys", qualifiedByName = "centsToKeys")
    @Mapping(target = "connectivityKeys", source = "keyWallet.connectivityKeys", qualifiedByName = "centsToKeys")
    @Mapping(target = "blockedPurchaseKeys", source = "keyWallet.blockedPurchaseKeys", qualifiedByName = "centsToKeys")
    @Mapping(target = "blockedConnectivityKeys", source = "keyWallet.blockedConnectivityKeys", qualifiedByName = "centsToKeys")
    @Mapping(target = "avatarUrl", source = "avatar.imageUrl")
    public abstract ConsumerInitialDataResponseDTO toConsumerInitialDataResponseDTO(ConsumerDetails consumer);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "email", source = "consumer.user.email")
    @Mapping(target = "phoneNumber", source = "consumer.user.phoneNumber")
    @Mapping(target = "role", source = "consumer.user.role")
    @Mapping(target = "userState", source = "consumer.user.userState")
    @Mapping(target = "department", ignore = true)
    public abstract ConsumerProfileResponseDTO toConsumerProfileResponseDTO(ConsumerDetails consumer);

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
    @Mapping(target = "age", ignore = true)
    @Mapping(target = "gender", ignore = true)
    @Mapping(target = "userHash", ignore = true)
    @Mapping(target = "hasPet", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "userName", ignore = true)
    @Mapping(target = "notifications", ignore = true)
    @Mapping(target = "user.email", source = "email")
    @Mapping(target = "user.phoneNumber", source = "phoneNumber")
    @Mapping(target = "municipality", ignore = true)
    @Mapping(target = "referredBy", ignore = true)
    @Mapping(target = "referrals", ignore = true)
    @Mapping(target = "keyWallet", ignore = true)
    @Mapping(target = "departmentName", ignore = true)
    @Mapping(target = "lastDailyLoginDate", ignore = true)
    @Mapping(target = "documentType", ignore = true)
    @Mapping(target = "documentNumber", ignore = true)
    @Mapping(target = "ocupacion", ignore = true)
    @Mapping(target = "ingresosMensualesRango", ignore = true)
    @Mapping(target = "esPEP", ignore = true)
    public abstract void updateConsumerFromDto(ConsumerUpdateProfileRequestDTO dto, @MappingTarget ConsumerDetails entity);
}