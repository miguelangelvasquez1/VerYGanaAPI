package com.verygana2.repositories.finance;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.KeyLotMovement;

@Repository
public interface KeyLotMovementRepository extends JpaRepository<KeyLotMovement, Long> {

    @Query("""
            SELECT m FROM KeyLotMovement m JOIN FETCH m.keyLot
            WHERE m.keyTransaction.id = :transactionId
            ORDER BY m.id ASC
            """)
    List<KeyLotMovement> findByTransactionId(@Param("transactionId") UUID transactionId);
}
