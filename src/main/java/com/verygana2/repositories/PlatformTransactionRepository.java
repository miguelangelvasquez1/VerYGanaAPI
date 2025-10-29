package com.verygana2.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.enums.PlatformTransactionType;
import com.verygana2.models.treasury.PlatformTransaction;

public interface PlatformTransactionRepository extends JpaRepository<PlatformTransaction, Long>{
    
    Optional<PlatformTransaction> findByReferenceId(String referenceId);
    List<PlatformTransaction> findByType(PlatformTransactionType type);

}
