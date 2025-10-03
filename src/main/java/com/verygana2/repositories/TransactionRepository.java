 package com.verygana2.repositories;

 import java.util.List;
 import java.util.Optional;

 import org.springframework.data.jpa.repository.JpaRepository;
 import org.springframework.stereotype.Repository;

import com.verygana2.models.Transaction;
import com.verygana2.models.enums.TransactionState;
import com.verygana2.models.enums.TransactionType;

 @Repository
 public interface TransactionRepository extends JpaRepository<Transaction, Long> {
     List<Transaction> findByTransactionTypeOrderByCreatedAtDesc(TransactionType transactionType);
     List<Transaction> findByTransactionStateOrderByCreatedAtDesc(TransactionState transactionState);
     List<Transaction> findByWalletIdOrderByCreatedAtDesc(Long walletId);
     List<Transaction> findByWalletIdAndTransactionType(Long walletId, TransactionType transactionType);
     List<Transaction> findByWalletIdAndTransactionState(Long walletId, TransactionState transactionState);
     Optional<Transaction> findByReferenceId (String referenceId);    
     boolean existsByReferenceId(String referenceId);
 }