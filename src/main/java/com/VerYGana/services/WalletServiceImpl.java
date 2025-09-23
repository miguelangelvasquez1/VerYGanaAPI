package com.VerYGana.services;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.VerYGana.dtos.Wallet.Requests.BlockBalanceRequest;
import com.VerYGana.dtos.Wallet.Requests.DepositRequest;
import com.VerYGana.dtos.Wallet.Requests.RafflePrizeRequest;
import com.VerYGana.dtos.Wallet.Requests.RechargeDataRequest;
import com.VerYGana.dtos.Wallet.Requests.TransferRequest;
import com.VerYGana.dtos.Wallet.Requests.UnblockBalanceRequest;
import com.VerYGana.dtos.Wallet.Requests.WalletCreateRequest;
import com.VerYGana.dtos.Wallet.Requests.WithdrawalRequest;
import com.VerYGana.dtos.Wallet.Responses.TransactionResponse;
import com.VerYGana.dtos.Wallet.Responses.WalletCreateResponse;
import com.VerYGana.dtos.Wallet.Responses.WalletResponse;
import com.VerYGana.exceptions.InsufficientFundsException;
import com.VerYGana.exceptions.InvalidAmountException;
import com.VerYGana.models.Transaction;
import com.VerYGana.models.Wallet;
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

    // Internal wallet methods

    // Creation
    @Override
    public WalletCreateResponse createWallet(WalletCreateRequest walletUserCreateRequest) {
        if (walletRepository.existsByUserId(walletUserCreateRequest.ownerId())) {
            throw new IllegalArgumentException("Wallet has already been registered for this user");
        }

        Wallet wallet = Wallet.createWallet(walletUserCreateRequest.ownerId());
        walletRepository.save(wallet);

        WalletCreateResponse response = new WalletCreateResponse(wallet.getBalance(), wallet.getBlockedBalance());

        return response;
    }
    
    // Get
    @Transactional(readOnly = true)
    @Override
    public WalletResponse getWalletByOwnerId(Long ownerId) {

        Wallet wallet = walletRepository.findByUserId(ownerId)
                .orElseThrow(
                        () -> new ObjectNotFoundException("Wallet not found for userId: " + ownerId, Wallet.class));

        WalletResponse response = new WalletResponse(wallet.getBalance(), wallet.getBlockedBalance(),
                wallet.getLastUpdated(), wallet.getTransactions());

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

        wallet.addBalance(depositRequest.amount());
        Transaction transaction = Transaction.createDepositTransaction(wallet.getId(), depositRequest.amount());
        transaction.setCompletedAt(ZonedDateTime.now(ZoneId.of("America/Bogota")));
        walletRepository.save(wallet);
        transactionRepository.save(transaction);

        TransactionResponse response = new TransactionResponse("Transacción exitosa", transaction.getAmount(),
                transaction.getReferenceId(), transaction.getCompletedAt());

        return response;
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
        Transaction transaction = Transaction.createWithdrawalTransaction(wallet.getId(), withdrawalRequest.amount());
        walletRepository.save(wallet);
        transactionRepository.save(transaction);

        TransactionResponse response = new TransactionResponse("Retiro exitoso", withdrawalRequest.amount(),
                transaction.getReferenceId(), ZonedDateTime.now(ZoneId.of("America/Bogota")));

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
        Transaction senderTransaction = Transaction.createGiftSentTransaction(senderWallet.getId(),
                transferRequest.amount(),
                mutualReferenceId);
        Transaction receiverTransaction = Transaction.createGiftReceivedTransaction(receiverWallet.getId(),
                transferRequest.amount(),
                mutualReferenceId);

        transactionRepository.save(senderTransaction);
        transactionRepository.save(receiverTransaction);

        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        TransactionResponse response = new TransactionResponse("Transferencia exitosa", transferRequest.amount(),
                mutualReferenceId, ZonedDateTime.now(ZoneId.of("America/Bogota")));

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

    // Balance Management

    @Override
    public TransactionResponse blockBalance(BlockBalanceRequest blockBalanceRequest) {

        Wallet wallet = walletRepository.findByUserId(blockBalanceRequest.userId()).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for userId: " + blockBalanceRequest.userId(),
                        Wallet.class));

        wallet.blockBalance(blockBalanceRequest.amount());
        walletRepository.save(wallet);
        // I think that create a new entity for save amount management reports instead
        // of using transactions would be a good idea, these reports would be used for
        // the admin.
        TransactionResponse response = new TransactionResponse(blockBalanceRequest.reason(),
                blockBalanceRequest.amount(), "", ZonedDateTime.now(ZoneId.of("America/Bogota")));
        return response;
    }

    @Override
    public TransactionResponse UnblockBalance(UnblockBalanceRequest unblockBalanceRequest) {

        Wallet wallet = walletRepository.findByUserId(unblockBalanceRequest.userId()).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for userId: " + unblockBalanceRequest.userId(),
                        Wallet.class));

        wallet.unblockBalance(unblockBalanceRequest.amount());
        walletRepository.save(wallet);
        // create a new entity for reports.
        TransactionResponse response = new TransactionResponse("Amount unblocked", unblockBalanceRequest.amount(), "",
                ZonedDateTime.now(ZoneId.of("America/Bogota")));
        return response;
    }

    // we does not know how we can make this method yet due to we need more
    // information
    @Override
    public TransactionResponse rechargeData(RechargeDataRequest rechargeDataRequest) {
        throw new UnsupportedOperationException("Unimplemented method 'rechargeData'");
    }

    // method used by user, this method is gonna be called by Adservice
    @Override
    public void addPointsForWatchingAdAndLike(Long userId, BigDecimal reward, Long advertiserId) {

        Wallet userWallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class));

        Wallet advertiserWallet = walletRepository.findByUserId(advertiserId).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for advertiserId: " + advertiserId, Wallet.class));

        advertiserWallet.subtractBalance(reward);
        userWallet.addBalance(reward);

        String mutualReferenceId = "MutualReferenceId-" + UUID.randomUUID().toString();
        Transaction advertiserTransaction = Transaction.createAdLikeRewardTransaction(advertiserWallet.getId(), reward,
                mutualReferenceId);
        Transaction userTransaction = Transaction.createAdLikeRewardTransaction(userWallet.getId(), reward,
                mutualReferenceId);

        transactionRepository.save(advertiserTransaction);
        transactionRepository.save(userTransaction);

        walletRepository.save(advertiserWallet);
        walletRepository.save(userWallet);

    }

    // method used by user, this method is gonna be called by userService or
    // ReferralService
    @Override
    public void addPointsForReferral(Long userId, BigDecimal amount, Long referredUserId) {

        Wallet userWallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class));
        Wallet referredUserWallet = walletRepository.findByUserId(referredUserId)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class));

        userWallet.addBalance(amount);
        referredUserWallet.addBalance(amount);

        String mutualReferenceId = "MutualReferenceId-" + UUID.randomUUID().toString();
        Transaction userTransaction = Transaction.createReferralRewardTransaction(userWallet.getId(), amount,
                mutualReferenceId);
        Transaction referredUseTransaction = Transaction.createReferralRewardTransaction(referredUserWallet.getId(),
                amount,
                mutualReferenceId);

        transactionRepository.save(userTransaction);
        transactionRepository.save(referredUseTransaction);

        walletRepository.save(userWallet);
        walletRepository.save(referredUserWallet);
    }

    // so far, we does not have the algorithm for raffle system
    @Override
    public void addRafflePrize(RafflePrizeRequest rafflePrizeRequest) {

    }

    // this method will be used for other service that manage the raffles section
    @Override
    public void participateInRaffle(Long userId, BigDecimal amount) {

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class));

        if (!wallet.hasSufficientBalance(amount)) {
            throw new InsufficientFundsException();
        }

        wallet.subtractBalance(amount);
        Transaction transaction = Transaction.createRaffleParticipationTransaction(wallet.getId(), amount);
        transactionRepository.save(transaction);
        walletRepository.save(wallet);

    }

    // This method will be used for other service that manage the marketplace
    // section
    @Override
    public void doPurchase(Long buyerId, BigDecimal amount, Long sellerId) {

        Wallet buyerWallet = walletRepository.findByUserId(buyerId).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for userId: " + buyerId, Wallet.class));

        if (!buyerWallet.hasSufficientBalance(amount)) {
            throw new InsufficientFundsException();
        }

        Wallet sellerWallet = walletRepository.findByUserId(sellerId).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for userId: " + sellerId, Wallet.class));

        buyerWallet.subtractBalance(amount);
        sellerWallet.addBalance(amount);

        String mutualReferenceId = UUID.randomUUID().toString();
        Transaction buyerTransaction = Transaction.createProductPurchaseTransaction(buyerWallet.getId(), amount,
                mutualReferenceId);
        Transaction sellerTransaction = Transaction.createProductSaleTransaction(sellerWallet.getId(), amount,
                mutualReferenceId);

        transactionRepository.save(buyerTransaction);
        transactionRepository.save(sellerTransaction);

        walletRepository.save(buyerWallet);
        walletRepository.save(sellerWallet);
    }
}
