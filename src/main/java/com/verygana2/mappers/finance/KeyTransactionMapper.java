package com.verygana2.mappers.finance;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Value;

import com.verygana2.dtos.finance.responses.KeyTransactionResponseDTO;
import com.verygana2.models.finance.KeyTransaction;

@Mapper(componentModel = "spring")
public abstract class KeyTransactionMapper {

    @Value("${financial.key-value-cents:1000}")
    protected long keyValueCents;

    @Named("centsToKeysDelta")
    protected Long centsToKeysDelta(Long cents) {
        return cents == null ? null : cents / keyValueCents;
    }

    @Mapping(target = "purchaseKeysDelta", source = "purchaseKeysDeltaCents", qualifiedByName = "centsToKeysDelta")
    @Mapping(target = "connectivityKeysDelta", source = "connectivityKeysDeltaCents", qualifiedByName = "centsToKeysDelta")
    public abstract KeyTransactionResponseDTO toKeyTransactionResponseDTO(KeyTransaction kt);
}
