package com.verygana2.services.interfaces.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.finance.responses.KeyTransactionResponseDTO;
import com.verygana2.models.enums.finance.KeyTransactionType;

public interface KeyTransactionService {

    Page<KeyTransactionResponseDTO> getByConsumerId(Long consumerId, Pageable pageable);

    Page<KeyTransactionResponseDTO> getByConsumerIdAndType(Long consumerId, KeyTransactionType type, Pageable pageable);
}
