package com.verygana2.services;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.Transaction;
import com.verygana2.models.Wallet;
import com.verygana2.repositories.TransactionRepository;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.services.interfaces.ReferralPromotionService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class ReferralPromotionServiceImpl implements ReferralPromotionService{

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final PlatformTreasuryServiceImpl platformTreasuryServiceImpl;

    @Override
    public void addPointsForReferral(Long userId, BigDecimal amount, Long userReferiedId) {
        Wallet userWallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class));
        Wallet referredUserWallet = walletRepository.findByUserId(userReferiedId)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class));

        userWallet.addBalance(amount);
        referredUserWallet.addBalance(amount);

        String mutualReferenceId = "MutualReferenceId-" + UUID.randomUUID().toString();
        Transaction userTransaction = Transaction.createReferralRewardTransaction(userWallet, amount,
                mutualReferenceId);
        Transaction referredUseTransaction = Transaction.createReferralRewardTransaction(referredUserWallet,
                amount,
                mutualReferenceId);

        transactionRepository.save(Objects.requireNonNull(userTransaction));
        transactionRepository.save(Objects.requireNonNull(referredUseTransaction));

        walletRepository.save(userWallet);
        walletRepository.save(referredUserWallet);
        
        platformTreasuryServiceImpl.recordReferralBonusPayout(amount, mutualReferenceId, String.format("Referral promotion for users ids: %d and %s", userId, userReferiedId));
    }
}
