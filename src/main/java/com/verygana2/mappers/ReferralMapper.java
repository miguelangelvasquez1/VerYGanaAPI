package com.verygana2.mappers;

import com.verygana2.dtos.referral.responses.ReferralItemDTO;
import com.verygana2.models.userDetails.ConsumerDetails;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReferralMapper {

    @Mapping(source = "user.email",          target = "email")
    @Mapping(source = "user.userState",      target = "userState")
    @Mapping(source = "user.registeredDate", target = "registeredDate")
    @Mapping(target = "municipality", source = "municipality.name")
    ReferralItemDTO toDTO(ConsumerDetails consumerDetails);
}