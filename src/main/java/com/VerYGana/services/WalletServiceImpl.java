package com.VerYGana.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.VerYGana.exceptions.InsufficientFunds;
import com.VerYGana.exceptions.InvalidAmountException;
import com.VerYGana.models.User;
import com.VerYGana.models.Advertiser;
import com.VerYGana.models.Transaction;
import com.VerYGana.models.Wallet;
import com.VerYGana.models.Enums.TransactionState;
import com.VerYGana.models.Enums.TransactionType;
import com.VerYGana.repositories.TransactionRepository;
import com.VerYGana.repositories.WalletRepository;
import com.VerYGana.services.interfaces.WalletService;

@Service
public class WalletServiceImpl implements WalletService {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    // devuelve la billetera del usuario por su ID
    @Override
    public Wallet getWalletByUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet", Wallet.class));
    }

    @Override
    public void createWallet(User user) {
        if (walletRepository.existsByUserId(user.getId())) {
            throw new IllegalArgumentException("Wallet has already been registered for this user");
        }

        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setBlockedBalance(BigDecimal.ZERO);
        wallet.setLastUpdated(LocalDateTime.now());
        walletRepository.save(wallet);
    }

    @Transactional
    @Override
    public void doIncome(User user, BigDecimal amount) {

        if (!walletRepository.existsByUserId(user.getId())) {
            throw new ObjectNotFoundException("Wallet", Wallet.class);
        }

        if (amount.compareTo(new BigDecimal(5000)) < 0 || amount.compareTo(new BigDecimal(5000000)) > 0) {
            throw new InvalidAmountException();
        }

        Wallet wallet = walletRepository.findByUserId(user.getId()).get();
        wallet.setBalance(wallet.getBalance().add(amount));
        wallet.setLastUpdated(LocalDateTime.now());

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setPayoutMethod(null);
        transaction.setAmount(amount);
        transaction.setCreatedAt(LocalDateTime.now());
        // logica de pasarela de pagos
        transaction.setCompletedAt(LocalDateTime.now());
        transaction.setTransactionType(TransactionType.INCOME);
        transaction.setDescription("Income completed");
        transaction.setTransactionState(TransactionState.COMPLETED);

        walletRepository.save(wallet);
        transactionRepository.save(transaction);
    }

    @Transactional
    @Override
    public void doWithdrawal(User user, BigDecimal amount) {

        if (!walletRepository.existsByUserId(user.getId())) {
            throw new ObjectNotFoundException("Wallet", Wallet.class);
        }

        if (amount.compareTo(new BigDecimal(20000)) < 0 || amount.compareTo(new BigDecimal(1000000)) > 0) {
            throw new InvalidAmountException();
        }

        Wallet wallet = walletRepository.findByUserId(user.getId()).get();

        if (amount.compareTo(wallet.getBalance()) == 1) {
            throw new InvalidAmountException();
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setLastUpdated(LocalDateTime.now());

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setPayoutMethod(null);
        transaction.setAmount(amount);
        transaction.setCreatedAt(LocalDateTime.now());
        // logica de pasarela de pagos
        transaction.setCompletedAt(LocalDateTime.now());
        transaction.setTransactionType(TransactionType.WITHDRAWAL);
        transaction.setDescription("Withdrawal completed");
        transaction.setTransactionState(TransactionState.COMPLETED);

        walletRepository.save(wallet);
        transactionRepository.save(transaction);

    }

    // This method does not make charges for sale yet
    @Transactional
    @Override
    public void doPurchase(User buyer, BigDecimal Tpoints, User seller) {
        if (!walletRepository.existsByUserId(buyer.getId())) {
            throw new ObjectNotFoundException("Wallet not found for userId: " + buyer.getId(), Wallet.class);
        }

        if (!walletRepository.existsByUserId(seller.getId())) {
            throw new ObjectNotFoundException("Wallet not found for userId: " + seller.getId(), Wallet.class);
        }

        Wallet buyerWallet = walletRepository.findByUserId(buyer.getId()).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for userId: " + buyer.getId(), Wallet.class));
        Wallet sellerWallet = walletRepository.findByUserId(seller.getId()).orElseThrow(
                () -> new ObjectNotFoundException("Wallet not found for userId: " + seller.getId(), Wallet.class));

        if (buyerWallet.getBalance().compareTo(Tpoints) == -1) {
            throw new InsufficientFunds();
        }

        if (Tpoints.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        buyerWallet.setBalance(buyerWallet.getBalance().subtract(Tpoints));
        buyerWallet.setLastUpdated(LocalDateTime.now());

        sellerWallet.setBalance(sellerWallet.getBalance().add(Tpoints));
        sellerWallet.setLastUpdated(LocalDateTime.now());

        Transaction buyerTransaction = new Transaction();
        buyerTransaction.setUser(buyer);
        buyerTransaction.setCreatedAt(LocalDateTime.now());
        buyerTransaction.setAmount(Tpoints);
        buyerTransaction.setPayoutMethod(null);
        buyerTransaction.setDescription("Marketplace Purchase");
        buyerTransaction.setTransactionState(TransactionState.COMPLETED);
        buyerTransaction.setTransactionType(TransactionType.PURCHASE);

        Transaction sellerTransaction = new Transaction();
        sellerTransaction.setUser(seller);
        sellerTransaction.setCreatedAt(LocalDateTime.now());
        sellerTransaction.setAmount(Tpoints);
        sellerTransaction.setPayoutMethod(null);
        sellerTransaction.setDescription("Marketplace Sale");
        sellerTransaction.setTransactionState(TransactionState.COMPLETED);
        sellerTransaction.setTransactionType(TransactionType.PURCHASE);

        String referenceId = UUID.randomUUID().toString();
        buyerTransaction.setReferenceId(referenceId);
        sellerTransaction.setReferenceId(referenceId);

        transactionRepository.save(buyerTransaction);
        transactionRepository.save(sellerTransaction);

        walletRepository.save(buyerWallet);
        walletRepository.save(sellerWallet);
    }

    @Transactional
    @Override
    public void addPointsForWatchingAdAndLike(User user, BigDecimal Tpoints, Advertiser advertiser) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addPointsForWatchingAdAndLike'");
    }

    @Override
    public void addPointsForReferral(User user, User referredUser, BigDecimal Tpoints) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addPointsForReferral'");
    }

    @Override
    public void addRafflePrize(User user, BigDecimal Tpoints) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addRafflePrize'");
    }

    @Override
    public void participateInRaffle(User user, BigDecimal Tpoints) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'participateInRaffle'");
    }

    @Override
    public void rechargeData(User user, BigDecimal Tpoints, String phoneNumber) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'rechargeData'");
    }

    @Override
    public void transferToUser(User sender, BigDecimal Tpoints, User receiver) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'transferToUser'");
    }

    @Override
    public BigDecimal getAvailableBalance(User user) {
        if (!walletRepository.existsByUserId(user.getId())) {
            throw new ObjectNotFoundException("Wallet not found for userId: " + user.getId(), Wallet.class);
        }
        return walletRepository.findByUserId(user.getId()).get().getBalance();
    }

    @Override
    public BigDecimal getBlockedBalance(User user) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getBlockedBalance'");
    }

    @Override
    public void BlockBalance(User user, BigDecimal Tpoints, String reason) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'BlockBalance'");
    }

    @Override
    public void UnBlockBalance(User user, BigDecimal Tpoints) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'UnBlockBalance'");
    }

}
