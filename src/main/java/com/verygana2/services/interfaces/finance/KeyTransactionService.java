package com.verygana2.services.interfaces.finance;

import java.time.ZonedDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.finance.responses.KeyTransactionResponseDTO;
import com.verygana2.models.enums.finance.KeyTransactionType;

public interface KeyTransactionService {

    Page<KeyTransactionResponseDTO> getByConsumerId(Long consumerId, ZonedDateTime initialDate, ZonedDateTime endDate, KeyTransactionType type, Pageable pageable);

    Long getTotalEarnedKeys (Long consumerId);
    Long getTotalUsedKeys (Long consumerId);
    Long getTotalExpiredKeys (Long consumerId);
}
