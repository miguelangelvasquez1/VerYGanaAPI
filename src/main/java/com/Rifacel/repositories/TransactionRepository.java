package com.Rifacel.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Rifacel.models.Transaction;
import com.Rifacel.models.Enums.TransactionState;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {
    List<Transaction> findByUserIdOrderByDateDesc(String userId);
    Optional<Transaction> findByReferenceCode (String referenceCode);
    List<Transaction> findByState(TransactionState state);
    boolean existsByReferenceCode(String referenceCode);
}