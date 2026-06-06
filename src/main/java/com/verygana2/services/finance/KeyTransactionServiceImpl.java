package com.verygana2.services.finance;

import java.time.ZonedDateTime;

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

    @Override
    public Page<KeyTransactionResponseDTO> getByConsumerId(Long consumerId, ZonedDateTime initialDate,
            ZonedDateTime endDate, KeyTransactionType type, Pageable pageable) {
        return keyTransactionRepository
                .findByConsumerId(consumerId, initialDate, endDate, type, pageable)
                .map(keyTransactionMapper::toKeyTransactionResponseDTO);
    }

    @Override
    public Long getTotalEarnedKeys(Long consumerId) {
        Long totalEarnedKeys = keyTransactionRepository.sumTotalEarnedKeys(consumerId);
        return totalEarnedKeys == null ? 0L : totalEarnedKeys;
    }

    @Override
    public Long getTotalUsedKeys(Long consumerId) {
        Long totalUsedKeys = keyTransactionRepository.sumTotalUsedKeys(consumerId);
        return totalUsedKeys == null ? 0L : totalUsedKeys;
    }

    @Override
    public Long getTotalExpiredKeys(Long consumerId) {
        Long totalExpiredKeys = keyTransactionRepository.sumTotalExpiredKeys(consumerId);
        return totalExpiredKeys == null ? 0L : totalExpiredKeys;
    }

}
