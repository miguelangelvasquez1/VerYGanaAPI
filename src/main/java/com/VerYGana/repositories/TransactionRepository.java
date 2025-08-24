 package com.VerYGana.repositories;

 import java.util.List;
 import java.util.Optional;

 import org.springframework.data.jpa.repository.JpaRepository;
 import org.springframework.stereotype.Repository;
 import com.VerYGana.models.Transaction;

 @Repository
 public interface TransactionRepository extends JpaRepository<Transaction, Long> {
     List<Transaction> findByUserIdOrderByCreatedAtDesc(Long userId);
     Optional<Transaction> findByReferenceId (String referenceCode);    
     boolean existsByReferenceId(String referenceCode);
 }