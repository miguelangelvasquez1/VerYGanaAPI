package com.verygana2.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.models.Transaction;
import com.verygana2.models.Wallet;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.repositories.RaffleRepository;
import com.verygana2.repositories.TransactionRepository;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.services.interfaces.RaffleService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class RaffleServiceImpl implements RaffleService{

    private WalletRepository walletRepository;
    private TransactionRepository transactionRepository;
    private RaffleRepository raffleRepository;

    // @Override
    // public List<Raffle> getByState(RaffleState state) {
    //     if (state == null || !(state == RaffleState.AVAILABLE || state == RaffleState.PENDING || state == RaffleState.FINISHED)) {
    //         throw new IllegalArgumentException("invalid raffle state");
    //     }
    //     return raffleRepository.findByState(state);
    // }

    @Override
    public Raffle getByName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("invalid raffle name");
        }
        return raffleRepository.findByName(name).orElseThrow(() -> new ObjectNotFoundException("Raffle", Raffle.class));
    }
    
    
    @Override
    public List<Raffle> getByDrawDateBefore(LocalDateTime dateTime) {
        return raffleRepository.findByDrawDateBefore(dateTime);
    }


    @Override
    public void addRafflePrize() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addRafflePrize'");
    }


    @Override
    public void raffleTicketSale(Long userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ObjectNotFoundException("Wallet not found for userId: " + userId, Wallet.class));

        if (!wallet.hasSufficientBalance(amount)) {
            throw new InsufficientFundsException();
        }

        wallet.subtractBalance(amount);
        Transaction transaction = Transaction.createRaffleParticipationTransaction(wallet, amount);
        transactionRepository.save(transaction);
        walletRepository.save(wallet);
    }
}
