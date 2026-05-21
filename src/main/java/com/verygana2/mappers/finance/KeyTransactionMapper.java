package com.verygana2.mappers.finance;

import org.mapstruct.Mapper;

import com.verygana2.dtos.finance.responses.KeyTransactionResponseDTO;
import com.verygana2.models.finance.KeyTransaction;

@Mapper(componentModel = "spring")
public interface KeyTransactionMapper {
    
    KeyTransactionResponseDTO toKeyTransactionResponseDTO (KeyTransaction kt);
}
