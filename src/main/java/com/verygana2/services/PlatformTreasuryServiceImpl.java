package com.verygana2.services;

import java.math.BigDecimal;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.treasury.PlatformTransaction;
import com.verygana2.models.treasury.PlatformTreasury;
import com.verygana2.repositories.PlatformTransactionRepository;
import com.verygana2.repositories.PlatformTreasuryRepository;
import com.verygana2.services.interfaces.PlatformTreasuryService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class PlatformTreasuryServiceImpl implements PlatformTreasuryService {

    private final PlatformTreasuryRepository platformTreasuryRepository;
    private final PlatformTransactionRepository platformTransactionRepository;

    @Override
    @Transactional(readOnly = true)
    public PlatformTreasury getTreasury() {
        return platformTreasuryRepository.findTreasury()
                .orElseThrow(() -> new ObjectNotFoundException("Platform treasury not found", PlatformTreasury.class));
    }

    @Override
    public void addProductSaleCommission(BigDecimal amount, String referenceId, String description) {
        PlatformTreasury treasury = getTreasury();
        treasury.addCommission(amount);
        PlatformTransaction platformTransaction = PlatformTransaction.createPurchaseCommission(amount, referenceId,
                description, treasury.getBalance(), treasury.getAvailableBalance());

        platformTreasuryRepository.save(treasury);
        platformTransactionRepository.save(platformTransaction);

    }

    @Override
    public void recordRealMoneyDeposit(BigDecimal amount, String paymentReference, String description) {
        PlatformTreasury treasury = getTreasury();
        treasury.addCommission(amount);
        PlatformTransaction platformTransaction = PlatformTransaction.createRealMoneyDeposit(amount, paymentReference,
                description, treasury.getBalance(), treasury.getAvailableBalance());
        platformTreasuryRepository.save(treasury);
        platformTransactionRepository.save(platformTransaction);

    }

    @Override
    public void reserveForWithdrawal(BigDecimal amount, String withdrawalReference, String description) {
        PlatformTreasury treasury = getTreasury();
        treasury.reserveForWithdrawal(amount);
        PlatformTransaction platformTransaction = PlatformTransaction.createWithdrawalReservation(amount,
                withdrawalReference, description, treasury.getBalance(), treasury.getAvailableBalance());
        platformTreasuryRepository.save(treasury);
        platformTransactionRepository.save(platformTransaction);
    }

    @Override
    public void completeWithdrawal(BigDecimal amount, String withdrawalReference, String description) {
        PlatformTreasury treasury = getTreasury();
        treasury.completeWithdrawal(amount);
        PlatformTransaction platformTransaction = PlatformTransaction.createWithdrawalCompleted(amount,
                withdrawalReference, description, treasury.getBalance(), treasury.getAvailableBalance());
        platformTreasuryRepository.save(treasury);
        platformTransactionRepository.save(platformTransaction);
    }

    @Override
    public void cancelWithdrawalReservation(BigDecimal amount, String withdrawalReference, String description) {
        PlatformTreasury treasury = getTreasury();
        treasury.cancelWithdrawalReservation(amount);
        PlatformTransaction platformTransaction = PlatformTransaction.createWithdrawalCancellation(amount,
                withdrawalReference, description, treasury.getBalance(), treasury.getAvailableBalance());
        platformTreasuryRepository.save(treasury);
        platformTransactionRepository.save(platformTransaction);
    }

    @Override
    public void addRaffleCommission(BigDecimal amount, String referenceId, String description) {
        PlatformTreasury treasury = getTreasury();
        treasury.addCommission(amount);
        PlatformTransaction platformTransaction = PlatformTransaction.createRaffleCommission(amount, referenceId,
                description, treasury.getBalance(), treasury.getAvailableBalance());
        platformTreasuryRepository.save(treasury);
        platformTransactionRepository.save(platformTransaction);
    }

    @Override
    public void addAdCommission(BigDecimal amount, String referenceId, String description) {
        PlatformTreasury treasury = getTreasury();
        treasury.addCommission(amount);
        PlatformTransaction platformTransaction = PlatformTransaction.createAdCommission(amount, referenceId,
                description, treasury.getBalance(), treasury.getAvailableBalance());
        platformTreasuryRepository.save(treasury);
        platformTransactionRepository.save(platformTransaction);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalBalance() {
        return getTreasury().getBalance();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getAvailableBalance() {
        return getTreasury().getAvailableBalance();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getReservedBalance() {
        return getTreasury().getReservedForWithdrawals();
    }

}
