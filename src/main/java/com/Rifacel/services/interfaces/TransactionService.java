package com.Rifacel.services.interfaces;

import java.util.List;


import com.Rifacel.models.Transaction;
import com.Rifacel.models.Enums.TransactionState;

public interface TransactionService {
    List<Transaction> getByUserIdOrderByDateDesc(String userId);
    Transaction getByReferenceCode(String referenceCode);
    List<Transaction> getByState(TransactionState state);
    boolean existsByReferenceCode(String referenceCode);
}
