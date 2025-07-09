package com.Rifacel.services;

import java.util.List;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Rifacel.models.Transaction;
import com.Rifacel.models.Enums.TransactionState;
import com.Rifacel.repositories.TransactionRepository;
import com.Rifacel.services.interfaces.TransactionService;


@Service
public class TransactionServiceImpl implements TransactionService {
    @Autowired
    private TransactionRepository transactionRepository;

    @Override
    public List<Transaction> getByUserIdOrderByDateDesc(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        return transactionRepository.findByUserIdOrderByDateDesc(userId);
    }

    @Override
    public Transaction getByReferenceCode(String referenceCode) {
        if (referenceCode == null || referenceCode.isBlank()) {
            throw new IllegalArgumentException("Reference code cannot be null or empty");
        }
        return transactionRepository.findByReferenceCode(referenceCode).orElseThrow(() -> new ObjectNotFoundException("Transaction", Transaction.class));
    }

    @Override
    public List<Transaction> getByState(TransactionState state) {
        if (state == null || !(state == TransactionState.ACCEPTED || state == TransactionState.PENDING || state == TransactionState.REJECTED)) {
            throw new IllegalArgumentException("invalid transaction state");
        }
        return transactionRepository.findByState(state);
    }

    @Override
    public boolean existsByReferenceCode(String referenceCode) {
        if (referenceCode == null || referenceCode.isBlank()) {
            throw new IllegalArgumentException("invalid reference code");
        }
        return transactionRepository.existsByReferenceCode(referenceCode);
    }
}
