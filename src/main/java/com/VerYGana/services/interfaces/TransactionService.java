package com.VerYGana.services.interfaces;

import java.util.List;

import com.VerYGana.models.Transaction;
import com.VerYGana.models.Enums.TransactionState;

public interface TransactionService {
    List<Transaction> getByUserIdOrderByDateDesc(String userId);
    Transaction getByReferenceCode(String referenceCode);
    List<Transaction> getByState(TransactionState state);
    boolean existsByReferenceCode(String referenceCode);
}
