package com.verygana2.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.payout.PayoutResponseDTO;
import com.verygana2.models.finance.Payout;

@Mapper(componentModel = "spring")
public interface PayoutMapper {
    
    @Mapping(target = "commercialPublicId", source = "commercial.user.publicId")
    @Mapping(target = "companyName", source = "commercial.companyName")
    PayoutResponseDTO toPayoutResponseDTO (Payout payout);
}
