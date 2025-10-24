package com.verygana2.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.verygana2.models.Transaction;
import com.verygana2.models.enums.TransactionState;
import com.verygana2.models.enums.TransactionType;
import com.verygana2.repositories.TransactionRepository;
import com.verygana2.services.interfaces.TransactionService;

@Service
public class TransactionServiceImpl implements TransactionService {
   @Autowired
   private TransactionRepository transactionRepository;

   @Override
   public List<Transaction> getByTransactionType(TransactionType transactionType) {
      if (transactionType == null) {

         throw new IllegalArgumentException("TransactionType cannot be null");
      }
      return transactionRepository.findByTransactionTypeOrderByCreatedAtDesc(transactionType);
   }

   @Override
   public List<Transaction> getByTransactionState(TransactionState transactionState) {
      if (transactionState == null) {

         throw new IllegalArgumentException("TransactionState cannot be null");
      }
      return transactionRepository.findByTransactionStateOrderByCreatedAtDesc(transactionState);
   }

   @Override
   public List<Transaction> getByWalletIdAndTransactionType(Long walletId, TransactionType transactionType) {
      if (walletId == null || walletId <= 0) {
         throw new IllegalArgumentException("WalletId null or not valid");
      }
      if (transactionType == null) {

         throw new IllegalArgumentException("TransactionType cannot be null");
      }
      return transactionRepository.findByWalletIdAndTransactionType(walletId, transactionType);
   }

   @Override
   public List<Transaction> getByWalletIdAndTransactionState(Long walletId, TransactionState transactionState) {
      if (walletId == null || walletId <= 0) {
         throw new IllegalArgumentException("WalletId null or not valid");
      }
      if (transactionState == null) {
         throw new IllegalArgumentException("TransactionState cannot be null");
      }
      return transactionRepository.findByWalletIdAndTransactionState(walletId, transactionState);
   }

   @Override
   public List<Transaction> getByWalletId(Long walletId) {
      if (walletId == null || walletId <= 0) {
         throw new IllegalArgumentException("WalletId null or not valid");
      }
      return transactionRepository.findByWalletIdOrderByCreatedAtDesc(walletId);
   }

   @Override
   public List<Transaction> getByReferenceId(String referenceId) {
      if (referenceId == null || referenceId.isBlank()) {
         throw new IllegalArgumentException("ReferenceId null or not valid");
      }
      return transactionRepository.findByReferenceId(referenceId);
   }

   @Override
   public boolean existsByReferenceId(String referenceId) {
      if (referenceId == null || referenceId.isBlank()) {
         throw new IllegalArgumentException("ReferenceId null or not valid");
      }
      return transactionRepository.existsByReferenceId(referenceId);
   }

}
