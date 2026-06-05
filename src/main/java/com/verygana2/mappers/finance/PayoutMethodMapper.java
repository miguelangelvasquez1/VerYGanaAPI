package com.verygana2.mappers.finance;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.verygana2.dtos.finance.requests.CreatePayoutMethodRequestDTO;
import com.verygana2.dtos.finance.responses.PayoutMethodResponseDTO;
import com.verygana2.models.finance.PayoutMethod;


@Mapper(componentModel = "spring")
public interface PayoutMethodMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "commercial", ignore = true)
    @Mapping(target = "verificationStatus", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "firstPayoutCompleted", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "verifiedAt", ignore = true)
    PayoutMethod toPayoutMethod(CreatePayoutMethodRequestDTO request);

    PayoutMethodResponseDTO toPayoutMethodResponseDTO(PayoutMethod payoutMethod);
}
