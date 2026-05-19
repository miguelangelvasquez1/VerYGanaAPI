package com.verygana2.repositories.finance;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.KeyTransaction;
import com.verygana2.models.enums.finance.KeyTransactionType;

@Repository
public interface KeyTransactionRepository extends JpaRepository<KeyTransaction, UUID> {

    @Query("SELECT kt FROM KeyTransaction kt WHERE kt.keyWallet.consumer.id = :consumerId ORDER BY kt.createdAt DESC")
    Page<KeyTransaction> findByConsumerId(@Param("consumerId") Long consumerId, Pageable pageable);

    @Query("SELECT kt FROM KeyTransaction kt WHERE kt.keyWallet.consumer.id = :consumerId AND kt.type = :type ORDER BY kt.createdAt DESC")
    Page<KeyTransaction> findByConsumerIdAndType(@Param("consumerId") Long consumerId, @Param("type") KeyTransactionType type, Pageable pageable);
}
