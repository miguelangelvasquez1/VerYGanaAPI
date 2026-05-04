package com.verygana2.services.finance;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.verygana2.models.finance.KeyWallet;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.finance.KeyWalletService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KeyWalletServiceImpl implements KeyWalletService {

    private final KeyWalletRepository keyWalletRepository;
    private final ConsumerDetailsService consumerDetailsService;

    @Override
    public void createFor(Long consumerId) {

        if (!keyWalletRepository.existsByConsumerId(consumerId)) {
            keyWalletRepository.save(
                    Objects.requireNonNull(KeyWallet.createFor(consumerDetailsService.getConsumerById(consumerId))));
        }
    }

    @Override
    public KeyWallet getByConsumerId(Long consumerId) {

        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        return keyWalletRepository.findByConsumerId(consumerId)
                .orElseThrow(() -> new EntityNotFoundException("Consumer with id: " + consumerId + " not found "));
    }

}
