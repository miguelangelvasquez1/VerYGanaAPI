package com.verygana2.services;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.wallet.requests.DepositRequest;
import com.verygana2.dtos.wallet.requests.TransferRequest;
import com.verygana2.dtos.wallet.requests.WithdrawalRequest;
import com.verygana2.dtos.wallet.responses.TransactionResponse;
import com.verygana2.dtos.wallet.responses.WalletResponse;
import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.exceptions.InvalidAmountException;
import com.verygana2.models.Transaction;
import com.verygana2.models.User;
import com.verygana2.models.Wallet;
import com.verygana2.repositories.TransactionRepository;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.services.interfaces.PlatformTreasuryService;
import com.verygana2.services.interfaces.WalletService;

import lombok.RequiredArgsConstructor;


@Service
@Transactional
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final PlatformTreasuryService platformTreasuryService;

    @Value("${usersEarnings.ad-view}")
    private BigDecimal adCommission;

    // Internal wallet methods

    // Creation
    @Override
    public void createWallet(User user) {
        if (walletRepository.existsByUserId(user.getId())) {
            throw new IllegalArgumentException("Wallet has already been registered for this user");
        }

        Wallet wallet = Wallet.createWallet(user);
        walletRepository.save(Objects.requireNonNull(wallet));
    }
    
    // Get
    @Transactional(readOnly = true)
    @Override
    public Wallet getByOwnerId(Long ownerId) {

         if (ownerId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }

        if (ownerId <= 0) {
            throw new IllegalArgumentException("UserId must be positive");
        }

        Wallet wallet = walletRepository.findByUserId(ownerId)
                .orElseThrow(
                        () -> new ObjectNotFoundException("Wallet not found for userId: " + ownerId, Wallet.class));


        return wallet;
    }

    
    @Transactional(readOnly = true)
    @Override
    public WalletResponse getWalletByOwnerId(Long ownerId) {

         if (ownerId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }

        if (ownerId <= 0) {
            throw new IllegalArgumentException("UserId must be positive");
        }

        Wallet wallet = walletRepository.findByUserId(ownerId)
                .orElseThrow(
                        () -> new ObjectNotFoundException("Wallet not found for userId: " + ownerId, Wallet.class));

        WalletResponse response = new WalletResponse(wallet.getBalance(), wallet.getBlockedBalance(),
                wallet.getLastUpdated());

        return response;
    }


    // Operations
    @Override
    public TransactionResponse doDeposit(Long userId, DepositRequest depositRequest) {

        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }

        if (userId <= 0) {
            throw new IllegalArgumentException("UserId must be positive");
        }

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(
                        () -> new ObjectNotFoundException("Wallet not found for ownerId: " + userId, Wallet.class));

        // Payment gateway logic

        // Simulate gateway commission (3%)
        BigDecimal gatewayFee = depositRequest.amount().multiply(new BigDecimal("0.03"));
        BigDecimal netAmount = depositRequest.amount().subtract(gatewayFee);

        wallet.addBalance(depositRequest.amount());
        Transaction transaction = Transaction.createDepositTransaction(wallet, depositRequest.amount(), depositRequest.paymentMethod());
        transaction.setCompletedAt(ZonedDateTime.now());
        walletRepository.save(wallet);
        transactionRepository.save(transaction);

        platformTreasuryService.recordRealMoneyDeposit(netAmount, transaction.getReferenceId(), String.format("Deposit from user %d. Gateway fee: %s", userId, gatewayFee));

        return new TransactionResponse("Deposit succesful", transaction.getAmount(),
                transaction.getReferenceId(), transaction.getCompletedAt());
    }

    @Override
    public TransactionResponse doWithdrawal(Long userId, WithdrawalRequest withdrawalRequest) {

        if (withdrawalRequest.amount().compareTo(new BigDecimal(20000)) < 0
                || withdrawalRequest.amount().compareTo(new BigDecimal(1000000)) > 0) {
            throw new InvalidAmountException("The amount must be between 20.000 and 1.000.000");
        }

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for userId: " + userId,
                        Wallet.class));

        if (!wallet.hasSufficientBalance(withdrawalRequest.amount())) {
            throw new InsufficientFundsException();
        }

        wallet.subtractBalance(withdrawalRequest.amount());
        Transaction transaction = Transaction.createWithdrawalTransaction(wallet, withdrawalRequest.amount(), withdrawalRequest.paymentMethod());
        walletRepository.save(wallet);
        transactionRepository.save(Objects.requireNonNull(transaction));

        platformTreasuryService.completeWithdrawal(withdrawalRequest.amount(), transaction.getReferenceId(), String.format("Withdrawal from user %d", userId));

        TransactionResponse response = new TransactionResponse("Witdrawal succesful", withdrawalRequest.amount(),
                transaction.getReferenceId(), ZonedDateTime.now());

        return response;
    }

    @Override
    public TransactionResponse transferToUser(Long senderId, TransferRequest transferRequest) {

        if (senderId == null) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        if (transferRequest.receiverId() == senderId) {
            throw new IllegalArgumentException("senderId and receiverId cannot be the same");
        }

        Wallet senderWallet = walletRepository.findByUserId(senderId).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for userId: " + senderId, Wallet.class));

        if (!senderWallet.hasSufficientBalance(transferRequest.amount())) {
            throw new InsufficientFundsException();
        }

        Wallet receiverWallet = walletRepository.findByUserId(transferRequest.receiverId())
                .orElseThrow(
                        () -> new ObjectNotFoundException(
                                "Wallet not found for userId: " + transferRequest.receiverId(), Wallet.class));

        senderWallet.subtractBalance(transferRequest.amount());
        receiverWallet.addBalance(transferRequest.amount());

        String mutualReferenceId = UUID.randomUUID().toString();
        Transaction senderTransaction = Transaction.createGiftSentTransaction(senderWallet,
                transferRequest.amount(),
                mutualReferenceId);
        Transaction receiverTransaction = Transaction.createGiftReceivedTransaction(receiverWallet,
                transferRequest.amount(),
                mutualReferenceId);

        transactionRepository.save(Objects.requireNonNull(senderTransaction));
        transactionRepository.save(Objects.requireNonNull(receiverTransaction));

        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        TransactionResponse response = new TransactionResponse("Transferencia exitosa", transferRequest.amount(),
                mutualReferenceId, ZonedDateTime.now());

        return response;
    }

    // Balance Queries

    @Transactional(readOnly = true)
    @Override
    public BigDecimal getAvailableBalance(Long userId) {

        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        return walletRepository.findByUserId(userId)
                .orElseThrow(
                        () -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class))
                .getBalance();
    }

    @Transactional(readOnly = true)
    @Override
    public BigDecimal getBlockedBalance(Long userId) {

        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        return walletRepository.findByUserId(userId)
                .orElseThrow(
                        () -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class))
                .getBlockedBalance();
    }

    // method used by user, this method is gonna be called by Adservice
    @Override
    public void addPointsForWatchingAdAndLike(Long userId, BigDecimal reward, Long advertiserId) {

        Wallet userWallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class));

        Wallet advertiserWallet = walletRepository.findByUserId(advertiserId).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for advertiserId: " + advertiserId, Wallet.class));

        advertiserWallet.subtractBalance(reward);
        BigDecimal platformCommission = reward.multiply(adCommission);
        userWallet.addBalance(reward.subtract(platformCommission));

        String mutualReferenceId = "MutualReferenceId-" + UUID.randomUUID().toString();
        Transaction advertiserTransaction = Transaction.createAdLikeRewardSentTransaction(advertiserWallet, reward,
                mutualReferenceId);
        Transaction userTransaction = Transaction.createAdLikeRewardReceivedTransaction(userWallet, reward,
                mutualReferenceId);

        transactionRepository.save(Objects.requireNonNull(advertiserTransaction));
        transactionRepository.save(Objects.requireNonNull(userTransaction));

        walletRepository.save(advertiserWallet);
        walletRepository.save(userWallet);

        platformTreasuryService.addAdCommission(platformCommission, mutualReferenceId, String.format("Ad commission from advertiser id: %d ", advertiserId));

    }

}
