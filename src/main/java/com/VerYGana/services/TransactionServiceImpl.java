package com.VerYGana.services;

import java.util.List;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.VerYGana.models.Transaction;
import com.VerYGana.repositories.TransactionRepository;
import com.VerYGana.services.interfaces.TransactionService;


@Service
public class TransactionServiceImpl implements TransactionService {
     @Autowired
      private TransactionRepository transactionRepository;

     @Override
     public List<Transaction> getByUserIdOrderByCreatedAtDesc(Long userId) {
        if (userId == null || userId <= 0L) {
            throw new IllegalArgumentException("User ID must be a positive number");
        }
         return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
     }

     @Override
     public Transaction getByReferenceId(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
             throw new IllegalArgumentException("Reference code cannot be null or empty");
         }
         return transactionRepository.findByReferenceId(referenceId).orElseThrow(() -> new ObjectNotFoundException("Transaction", Transaction.class));
     }

     @Override
     public boolean existsByReferenceId(String referenceId) {
         if (referenceId == null || referenceId.isBlank()) {
             throw new IllegalArgumentException("invalid reference code");
         }
         return transactionRepository.existsByReferenceId(referenceId);
     }
}
