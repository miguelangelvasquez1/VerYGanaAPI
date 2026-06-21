package com.verygana2.services.finance;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;

import com.verygana2.dtos.keys.KeyBalanceResponseDTO;
import com.verygana2.dtos.keys.SpendKeysRequestDTO;
import com.verygana2.dtos.keys.SpendKeysResponseDTO;
import com.verygana2.models.finance.KeyTransaction;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${financial.purchase-keys-percentage:75}")
    private Long PURCHASE_KEYS_PERCENTAGE;
    private static final int PERCENTAGE_BASE = 100;

    private static final ZoneId COLOMBIA_ZONE = ZoneId.of("America/Bogota");

    private final Clock clock;
    private final KeyTransactionRepository keyTransactionRepository;
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

    // Calcula las dos recomepnsas a partir de la cantidad total de llaves
    @Override
    public RewardSplit calculate(long totalRewardKeysCents) {

        if (totalRewardKeysCents <= 0) {
            return new RewardSplit(0, 0);
        }

        long multiplied = Math.multiplyExact(totalRewardKeysCents, PURCHASE_KEYS_PERCENTAGE);

        long purchaseKeysReward = Math.floorDiv(multiplied + (PERCENTAGE_BASE / 2), PERCENTAGE_BASE);
        long connectivityKeysReward = totalRewardKeysCents - purchaseKeysReward;

        return new RewardSplit(purchaseKeysReward, connectivityKeysReward);
    }

    @Override
    public ZonedDateTime calculatePurchaseExpiry() {
        ZonedDateTime nowColombia = ZonedDateTime.now(clock).withZoneSameInstant(COLOMBIA_ZONE);
        return nowColombia.toLocalDate()
                .withDayOfMonth(1)
                .plusMonths(1)
                .atStartOfDay(COLOMBIA_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC);
    }

    @Override
    public ZonedDateTime calculateConnectivityExpiry() {
        ZonedDateTime nowColombia = ZonedDateTime.now(clock).withZoneSameInstant(COLOMBIA_ZONE);
        return nowColombia.plusDays(1).withZoneSameInstant(ZoneOffset.UTC);
    }

    public record RewardSplit(
            long purchaseKeysReward,
            long connectivityKeysReward
    ) {}

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