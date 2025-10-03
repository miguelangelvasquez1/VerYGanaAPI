package com.verygana2.services.interfaces;

import java.util.List;

import com.verygana2.models.Transaction;
import com.verygana2.models.enums.TransactionState;
import com.verygana2.models.enums.TransactionType;

public interface TransactionService {
     List<Transaction> getByTransactionType(TransactionType transactionType);
     List<Transaction> getByTransactionState(TransactionState transactionState);
     List<Transaction> getByWalletIdAndTransactionType(Long walletId, TransactionType transactionType);
     List<Transaction> getByWalletIdAndTransactionState(Long walletId, TransactionState transactionState);
     List<Transaction> getByWalletId(Long walletId);
     Transaction getByReferenceId(String referenceId);
     boolean existsByReferenceId(String referenceId);
}
