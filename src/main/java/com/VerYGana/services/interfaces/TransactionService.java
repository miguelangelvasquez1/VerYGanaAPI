package com.VerYGana.services.interfaces;

import java.util.List;

import com.VerYGana.models.Transaction;
import com.VerYGana.models.enums2.TransactionState;
import com.VerYGana.models.enums2.TransactionType;

public interface TransactionService {
     List<Transaction> getByTransactionType(TransactionType transactionType);
     List<Transaction> getByTransactionState(TransactionState transactionState);
     List<Transaction> getByWalletIdAndTransactionType(Long walletId, TransactionType transactionType);
     List<Transaction> getByWalletIdAndTransactionState(Long walletId, TransactionState transactionState);
     List<Transaction> getByWalletId(Long walletId);
     Transaction getByReferenceId(String referenceId);
     boolean existsByReferenceId(String referenceId);
}
