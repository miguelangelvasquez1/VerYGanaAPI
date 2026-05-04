package com.verygana2.services.finance;

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
    public Page<KeyTransactionResponseDTO> getByConsumerId(Long consumerId, Pageable pageable) {
        return keyTransactionRepository
                .findByConsumerId(consumerId, pageable)
                .map(keyTransactionMapper::toKeyTransactionResponseDTO);
    }

    @Override
    public Page<KeyTransactionResponseDTO> getByConsumerIdAndType(Long consumerId, KeyTransactionType type, Pageable pageable) {
        return keyTransactionRepository
                .findByConsumerIdAndType(consumerId, type, pageable)
                .map(keyTransactionMapper::toKeyTransactionResponseDTO);
    }

}
