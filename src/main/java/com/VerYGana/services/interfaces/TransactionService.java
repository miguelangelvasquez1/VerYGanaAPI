package com.VerYGana.services.interfaces;

import java.util.List;

import com.VerYGana.models.Transaction;

public interface TransactionService {
     List<Transaction> getByUserIdOrderByCreatedAtDesc(Long userId);
     Transaction getByReferenceId(String referenceCode);
     boolean existsByReferenceId(String referenceCode);
}
