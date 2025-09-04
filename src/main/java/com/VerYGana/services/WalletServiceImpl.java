package com.VerYGana.services;

import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.VerYGana.DTOS.Wallet.Requests.DepositRequest;
import com.VerYGana.DTOS.Wallet.Requests.WalletAdvertiserCreateRequest;
import com.VerYGana.DTOS.Wallet.Requests.WalletUserCreateRequest;
import com.VerYGana.DTOS.Wallet.Responses.TransactionResponse;
import com.VerYGana.DTOS.Wallet.Responses.WalletResponse;
import com.VerYGana.exceptions.InsufficientFundsException;
import com.VerYGana.exceptions.InvalidAmountException;
import com.VerYGana.models.User;
import com.VerYGana.models.Advertiser;
import com.VerYGana.models.Transaction;
import com.VerYGana.models.Wallet;
import com.VerYGana.models.Enums.WalletOwnerType;
import com.VerYGana.repositories.TransactionRepository;
import com.VerYGana.repositories.WalletRepository;
import com.VerYGana.services.interfaces.WalletService;

@Transactional
@Service
public class WalletServiceImpl implements WalletService {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Override
    public WalletResponse createWalletForUser(WalletUserCreateRequest walletUserCreateRequest) {
        if (walletRepository.existsByOwnerIdAndOwnerType(walletUserCreateRequest.userId(), WalletOwnerType.USER)) {
            throw new IllegalArgumentException("Wallet has already been registered for this user");
        }

        Wallet wallet = Wallet.createWallet(walletUserCreateRequest.userId(), WalletOwnerType.USER);
        walletRepository.save(wallet);

        WalletResponse walletResponse = new WalletResponse(wallet.getId(), wallet.getOwnerId(), wallet.getOwnerType(),
                wallet.getBalance(), wallet.getBlockedBalance(), wallet.getLastUpdated());

        return walletResponse;
    }

    @Override
    public WalletResponse createWalletForAdvertiser(WalletAdvertiserCreateRequest walletAdvertiserCreateRequest) {
        if (walletRepository.existsByOwnerIdAndOwnerType(walletAdvertiserCreateRequest.advertiserId(),
                WalletOwnerType.ADVERTISER)) {
            throw new IllegalArgumentException("Wallet has already been registered for this advertiser");
        }

        Wallet wallet = Wallet.createWallet(walletAdvertiserCreateRequest.advertiserId(), WalletOwnerType.ADVERTISER);
        walletRepository.save(wallet);

        WalletResponse walletResponse = new WalletResponse(wallet.getId(), wallet.getOwnerId(), wallet.getOwnerType(),
                wallet.getBalance(), wallet.getBlockedBalance(), wallet.getLastUpdated());

        return walletResponse;
    }

    @Transactional(readOnly = true)
    @Override
    public WalletResponse getUserWalletByUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        Wallet wallet = walletRepository.findByOwnerIdAndOwnerType(userId, WalletOwnerType.USER)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class));
        WalletResponse response = new WalletResponse(wallet.getId(), wallet.getOwnerId(), wallet.getOwnerType(),
                wallet.getBalance(), wallet.getBlockedBalance(), wallet.getLastUpdated());

        return response;
    }

    @Transactional(readOnly = true)
    @Override
    public WalletResponse getAdvertiserWalletByAdvertiserId(Long advertiserId) {
        if (advertiserId == null) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }
        Wallet wallet = walletRepository.findByOwnerIdAndOwnerType(advertiserId, WalletOwnerType.ADVERTISER)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for advertiserId: " + advertiserId,
                        Wallet.class));
        WalletResponse response = new WalletResponse(wallet.getId(), wallet.getOwnerId(), wallet.getOwnerType(),
                wallet.getBalance(), wallet.getBlockedBalance(), wallet.getLastUpdated());
        return response;
    }

    @Override
    public TransactionResponse doDeposit(Long ownerId, WalletOwnerType walletOwnerType, DepositRequest depositRequest) {


        Wallet wallet = walletRepository.findByOwnerIdAndOwnerType(ownerId, walletOwnerType)
                .orElseThrow(
                        () -> new ObjectNotFoundException("Wallet not found for ownerId: " + ownerId, Wallet.class));
        wallet.addBalance(depositRequest.amount());
        Transaction transaction = Transaction.createDepositTransaction(wallet, walletOwnerType, amount);
        walletRepository.save(wallet);
        transactionRepository.save(transaction);

        TransactionResponse response = new TransactionResponse(transaction.getDescription(), transaction.getAmount(), transaction.getReferenceId(), transaction.get);

    }

    @Override
    public void addPointsForWatchingAdAndLike(Long userId, BigDecimal Tpoints, Long advertiserId) {

        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        if (advertiserId == null) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        Wallet userWallet = walletRepository.findByOwnerIdAndOwnerType(userId, WalletOwnerType.USER)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class));
        Wallet advertiserWallet = walletRepository.findByOwnerIdAndOwnerType(advertiserId, WalletOwnerType.ADVERTISER)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for advertiserId: " + advertiserId,
                        Wallet.class));

        advertiserWallet.subtractBalance(Tpoints);
        userWallet.addBalance(Tpoints);

        String mutualReferenceId = "MutualReferenceId-" + UUID.randomUUID().toString();
        Transaction advertiserTransaction = Transaction.createAdLikeRewardTransaction(advertiserWallet, Tpoints,
                mutualReferenceId);
        Transaction userTransaction = Transaction.createAdLikeRewardTransaction(userWallet, Tpoints, mutualReferenceId);

        transactionRepository.save(advertiserTransaction);
        transactionRepository.save(userTransaction);

        walletRepository.save(advertiserWallet);
        walletRepository.save(userWallet);

    }

    @Override
    public void addPointsForReferral(Long userId, Long referredUserId, BigDecimal Tpoints) {

        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        if (referredUserId == null) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        Wallet userWallet = walletRepository.findByOwnerIdAndOwnerType(userId, WalletOwnerType.USER)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class));
        Wallet referredUserWallet = walletRepository.findByOwnerIdAndOwnerType(referredUserId, WalletOwnerType.USER)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class));

        userWallet.addBalance(Tpoints);
        referredUserWallet.addBalance(Tpoints);

        String mutualReferenceId = "MutualReferenceId-" + UUID.randomUUID().toString();
        Transaction userTransaction = Transaction.createReferralRewardTransaction(userWallet, Tpoints,
                mutualReferenceId);
        Transaction referredUseTransaction = Transaction.createReferralRewardTransaction(referredUserWallet, Tpoints,
                mutualReferenceId);

        transactionRepository.save(userTransaction);
        transactionRepository.save(referredUseTransaction);

        walletRepository.save(userWallet);
        walletRepository.save(referredUserWallet);
    }

    @Override
    public void addRafflePrize(Long userId, BigDecimal Tpoints) {

        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        Wallet wallet = walletRepository.findByOwnerIdAndOwnerType(userId, WalletOwnerType.USER)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class));
        wallet.addBalance(Tpoints);
        Transaction transaction = Transaction.createRafflePrizeTransaction(wallet, Tpoints);
        transactionRepository.save(transaction);
        walletRepository.save(wallet);
    }

    // Purchases and others
    @Override
    public void doWithdrawal(Long advertiserId, BigDecimal amount) {

        if (advertiserId == null) {
            throw new IllegalArgumentException("AdvertiserId cannot be null or empty");
        }

        if (amount.compareTo(new BigDecimal(20000)) < 0 || amount.compareTo(new BigDecimal(1000000)) > 0) {
            throw new InvalidAmountException("The amount must be between 20.000 and 1.000.000");
        }

        Wallet wallet = walletRepository.findByOwnerIdAndOwnerType(advertiserId, WalletOwnerType.ADVERTISER)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for advertiserId: " + advertiserId,
                        Wallet.class));

        if (!wallet.hasSufficientBalance(amount)) {
            throw new InsufficientFundsException();
        }

        wallet.subtractBalance(amount);
        Transaction transaction = Transaction.createWithdrawalTransaction(wallet, amount);
        walletRepository.save(wallet);
        transactionRepository.save(transaction);

    }

    @Override
    public void participateInRaffle(Long userId, BigDecimal Tpoints) {

        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        Wallet wallet = walletRepository.findByOwnerIdAndOwnerType(userId, WalletOwnerType.USER)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class));

        if (!wallet.hasSufficientBalance(Tpoints)) {
            throw new InsufficientFundsException();
        }

        wallet.subtractBalance(Tpoints);
        Transaction transaction = Transaction.createRaffleParticipationTransaction(wallet, Tpoints);
        transactionRepository.save(transaction);
        walletRepository.save(wallet);

    }

    @Override
    public void rechargeData(Long userId, BigDecimal Tpoints, String phoneNumber) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'rechargeData'");
    }

    @Override
    public void transferToUser(Long senderId, BigDecimal Tpoints, Long receiverId) {

        if (senderId == null) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        if (receiverId == null) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        Wallet senderWallet = walletRepository.findByOwnerIdAndOwnerType(senderId, WalletOwnerType.USER).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for userId: " + senderId, Wallet.class));

        Wallet receiverWallet = walletRepository.findByOwnerIdAndOwnerType(receiverId, WalletOwnerType.USER)
                .orElseThrow(
                        () -> new ObjectNotFoundException("Wallet not found for userId: " + receiverId, Wallet.class));

        senderWallet.subtractBalance(Tpoints);
        receiverWallet.addBalance(Tpoints);

        String mutualReferenceId = UUID.randomUUID().toString();
        Transaction senderTransaction = Transaction.createGiftSentTransaction(senderWallet, Tpoints,
                mutualReferenceId);
        Transaction receiverTransaction = Transaction.createGiftReceivedTransaction(receiverWallet, Tpoints,
                mutualReferenceId);

        transactionRepository.save(senderTransaction);
        transactionRepository.save(receiverTransaction);

        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

    }

    // This method does not make charges for sale yet
    @Override
    public void doPurchase(Long buyerId, BigDecimal Tpoints, Long sellerId) {

        if (buyerId == null) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        if (sellerId == null) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        Wallet buyerWallet = walletRepository.findByOwnerIdAndOwnerType(buyerId, WalletOwnerType.USER).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for userId: " + buyerId, Wallet.class));

        if (!buyerWallet.hasSufficientBalance(Tpoints)) {
            throw new InsufficientFundsException();
        }

        Wallet sellerWallet = walletRepository.findByOwnerIdAndOwnerType(sellerId, WalletOwnerType.USER).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for userId: " + sellerId, Wallet.class));

        buyerWallet.subtractBalance(Tpoints);
        sellerWallet.addBalance(Tpoints);

        String mutualReferenceId = UUID.randomUUID().toString();
        Transaction buyerTransaction = Transaction.createProductPurchaseTransaction(buyerWallet, Tpoints,
                mutualReferenceId);
        Transaction sellerTransaction = Transaction.createProductSaleTransaction(sellerWallet, Tpoints,
                mutualReferenceId);

        transactionRepository.save(buyerTransaction);
        transactionRepository.save(sellerTransaction);

        walletRepository.save(buyerWallet);
        walletRepository.save(sellerWallet);
    }

    // Balance Queries

    @Transactional(readOnly = true)
    @Override
    public BigDecimal getAvailableBalance(Long userId) {

        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        return walletRepository.findByOwnerIdAndOwnerType(userId, WalletOwnerType.USER)
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

        return walletRepository.findByOwnerIdAndOwnerType(userId, WalletOwnerType.USER)
                .orElseThrow(
                        () -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class))
                .getBlockedBalance();
    }

    // Balance Management

    @Override
    public void blockBalance(Long userId, BigDecimal Tpoints, String reason) {

        Wallet wallet = walletRepository.findByOwnerIdAndOwnerType(userId, WalletOwnerType.USER).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class));

        wallet.blockBalance(Tpoints);
        walletRepository.save(wallet);
    }

    @Override
    public void UnblockBalance(Long userId, BigDecimal Tpoints) {

        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        Wallet wallet = walletRepository.findByOwnerIdAndOwnerType(userId, WalletOwnerType.USER).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class));

        wallet.unblockBalance(Tpoints);
        walletRepository.save(wallet);
    }

}
