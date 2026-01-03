package com.verygana2.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.dtos.transaction.responses.TransactionResponseDTO;
import com.verygana2.models.Transaction;
import com.verygana2.models.enums.TransactionState;
import com.verygana2.models.enums.TransactionType;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
   @Query("""
         SELECT new com.verygana2.dtos.transaction.responses.TransactionResponseDTO(
            t.id,
            t.amount,
            t.createdAt,
            t.paymentMethod,
            t.referenceId,
            t.transactionType,
            t.transactionState)
            FROM Transaction t
         WHERE t.wallet.id = :walletId
         """)
   Page<TransactionResponseDTO> findByWalletId(@Param("walletId") Long walletId, Pageable pageable);

   @Query("""
         SELECT new com.verygana2.dtos.transaction.responses.TransactionResponseDTO(
            t.id,
            t.amount,
            t.createdAt,
            t.paymentMethod,
            t.referenceId,
            t.transactionType,
            t.transactionState)
            FROM Transaction t
         WHERE t.wallet.id = :walletId AND t.transactionType = :type
         """)
   Page<TransactionResponseDTO> findByWalletIdAndTransactionType(@Param("walletId") Long walletId,
         @Param("type") TransactionType transactionType, Pageable pageable);

   @Query("""
         SELECT new com.verygana2.dtos.transaction.responses.TransactionResponseDTO(
            t.id,
            t.amount,
            t.createdAt,
            t.paymentMethod,
            t.referenceId,
            t.transactionType,
            t.transactionState)
         FROM Transaction t
         WHERE t.wallet.id = :walletId AND t.transactionState = :state
         """)
   Page<TransactionResponseDTO> findByWalletIdAndTransactionState(@Param("walletId") Long walletId,
         @Param("state") TransactionState transactionState, Pageable pageable);

   @Query("""
         SELECT new com.verygana2.dtos.transaction.responses.TransactionResponseDTO(
            t.id,
            t.amount,
            t.createdAt,
            t.paymentMethod,
            t.referenceId,
            t.transactionType,
            t.transactionState)
         FROM Transaction t
         WHERE t.referenceId LIKE CONCAT('%', :referenceId, '%') 
            AND t.wallet.user.id = :userId
         """)
   Page<TransactionResponseDTO> findByReferenceId(@Param("userId") Long userId, @Param("referenceId") String referenceId, Pageable pageable);

   Optional<Transaction> findByWompiTransactionId(String wompiTransactionId);

   Long countByWalletIdAndTransactionType(Long walletId, TransactionType transactionType);

   @Query("""
            SELECT SUM(t.amount) FROM Transaction t
            WHERE (t.transactionType = com.verygana2.models.enums.TransactionType.POINTS_AD_LIKE_REWARD OR t.transactionType = com.verygana2.models.enums.TransactionType.POINTS_GAME_PLAYED)
                  AND t.wallet.id = :walletId
         """)
   Long sumUserEarningsByWalletId(@Param("walletId") Long walletId);
}