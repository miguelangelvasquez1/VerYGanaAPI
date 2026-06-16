package com.verygana2.services.finance;

import java.util.Objects;

import com.verygana2.dtos.keys.KeyBalanceResponseDTO;
import com.verygana2.dtos.keys.SpendKeysRequestDTO;
import com.verygana2.dtos.keys.SpendKeysResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import com.verygana2.models.finance.KeyTransaction;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.finance.KeyWalletService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KeyWalletServiceImpl implements KeyWalletService {

    private final KeyWalletRepository keyWalletRepository;
    private final KeyTransactionRepository keyTransactionRepository;
    private final ConsumerDetailsService consumerDetailsService;

    @Override
    public void createFor(Long consumerId) {
        if (!keyWalletRepository.existsByConsumerId(consumerId)) {
            keyWalletRepository.save(
                    Objects.requireNonNull(
                            KeyWallet.createFor(consumerDetailsService.getConsumerById(consumerId))));
        }
    }

    @Override
    public KeyWallet getByConsumerId(Long consumerId) {
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }
        return keyWalletRepository.findByConsumerId(consumerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Consumer with id: " + consumerId + " not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public KeyBalanceResponseDTO getBalance(Long consumerId) {
        KeyWallet wallet = getByConsumerId(consumerId);
        return new KeyBalanceResponseDTO(wallet.getAvailableKeys(), "keys");
    }

    @Override
    @Transactional
    public SpendKeysResponseDTO spendKeysForPetGame(Long consumerId, SpendKeysRequestDTO request) {
        KeyWallet wallet = getByConsumerId(consumerId);

        if (!wallet.hasSufficientPurchaseKeys(request.amount())) {
            return SpendKeysResponseDTO.fail("Saldo insuficiente");
        }

        wallet.expirePurchaseKeys(request.amount());
        keyWalletRepository.save(wallet);

        keyTransactionRepository.save(
                KeyTransaction.forPetGame(wallet, request.amount(), request.itemName()));

        return SpendKeysResponseDTO.ok(wallet.getAvailableKeys());
    }
}