package com.verygana2.services.finance;

import java.time.ZonedDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.finance.responses.KeyTransactionResponseDTO;
import com.verygana2.mappers.finance.KeyTransactionMapper;
import com.verygana2.models.enums.finance.KeyTransactionType;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.services.interfaces.finance.KeyTransactionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KeyTransactionServiceImpl implements KeyTransactionService {

    private final KeyTransactionRepository keyTransactionRepository;
    private final KeyTransactionMapper keyTransactionMapper;

    @Value("${financial.key-value-cents:1000}")
    private long keyValueCents;

    @Override
    public Page<KeyTransactionResponseDTO> getByConsumerId(Long consumerId, ZonedDateTime initialDate,
            ZonedDateTime endDate, KeyTransactionType type, Pageable pageable) {
        return keyTransactionRepository
                .findByConsumerId(consumerId, initialDate, endDate, type, pageable)
                .map(keyTransactionMapper::toKeyTransactionResponseDTO);
    }

    @Override
    public Long getTotalEarnedKeys(Long consumerId) {
        Long totalEarnedKeysCents = keyTransactionRepository.sumTotalEarnedKeysCents(consumerId);
        return totalEarnedKeysCents == null ? 0L : totalEarnedKeysCents / keyValueCents;
    }

    @Override
    public Long getTotalUsedKeys(Long consumerId) {
        Long totalUsedKeysCents = keyTransactionRepository.sumTotalUsedKeysCents(consumerId);
        return totalUsedKeysCents == null ? 0L : totalUsedKeysCents / keyValueCents;
    }

    @Override
    public Long getTotalExpiredKeys(Long consumerId) {
        Long totalExpiredKeysCents = keyTransactionRepository.sumTotalExpiredKeysCents(consumerId);
        return totalExpiredKeysCents == null ? 0L : totalExpiredKeysCents / keyValueCents;
    }

}
