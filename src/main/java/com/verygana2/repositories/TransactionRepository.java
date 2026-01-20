package com.verygana2.repositories;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.dtos.seller.responses.EarningsByMonthResponseDTO;
import com.verygana2.dtos.transaction.responses.TransactionPayoutResponseDTO;
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
   Page<TransactionResponseDTO> findByReferenceId(@Param("userId") Long userId,
         @Param("referenceId") String referenceId, Pageable pageable);

   Optional<Transaction> findByWompiTransactionId(String wompiTransactionId);

   Long countByWalletIdAndTransactionType(Long walletId, TransactionType transactionType);

   @Query("""
            SELECT SUM(t.amount) FROM Transaction t
            WHERE (t.transactionType = com.verygana2.models.enums.TransactionType.POINTS_AD_LIKE_REWARD OR t.transactionType = com.verygana2.models.enums.TransactionType.POINTS_GAME_PLAYED)
                  AND t.wallet.id = :walletId
         """)
   BigDecimal sumUserEarningsByWalletId(@Param("walletId") Long walletId);

   @Query("""
         SELECT SUM(t.amount)
         FROM Transaction t
         WHERE t.wallet.user.id = :sellerId
         AND t.transactionType = com.verygana2.models.enums.TransactionType.PRODUCT_SALE
         """)
   BigDecimal sumTotalSellerEarningsAmount(@Param("sellerId") Long sellerId);

   @Query("""
         SELECT new com.verygana2.dtos.seller.responses.EarningsByMonthResponseDTO(
         t.wallet.user.id,
         :year,
         MONTH(t.createdAt),
         SUM(t.amount)
         )
         FROM Transaction t
         WHERE t.wallet.user.id = :sellerId
         AND t.transactionType = com.verygana2.models.enums.TransactionType.PRODUCT_SALE
         AND YEAR(t.createdAt) = :year
         GROUP BY MONTH(t.createdAt)
         ORDER BY MONTH(t.createdAt)
         """)
   List<EarningsByMonthResponseDTO> findSellerEarningsByYear(@Param("sellerId") Long sellerId,
         @Param("year") Integer year);

   @Query("""
         SELECT SUM(t.amount)
         FROM Transaction t
         WHERE t.wallet.user.id = :sellerId
         AND t.transactionType = com.verygana2.models.enums.TransactionType.PRODUCT_SALE
         AND t.createdAt >= :startDate
         AND t.createdAt < :endDate
           """)
   BigDecimal findSellerEarningsByMonth(@Param("sellerId") Long sellerId, @Param("startDate") ZonedDateTime startDate,
         @Param("endDate") ZonedDateTime endDate);

   @Query("SELECT new com.verygana2.dtos.transaction.responses.TransactionPayoutResponseDTO(" +
         "t.id, t.referenceId, t.createdAt, t.amount, t.transactionState) " +
         "FROM Transaction t " +
         "WHERE t.wallet.user.id = :sellerId " +
         "AND t.transactionType = com.verygana2.models.enums.TransactionType.PRODUCT_SALE " +
         "AND t.createdAt >= :startDate " +
         "AND t.createdAt < :endDate " +
         "ORDER BY t.createdAt DESC")
   Page<TransactionPayoutResponseDTO> findSellerPayouts(
         @Param("sellerId") Long sellerId,
         @Param("startDate") ZonedDateTime startDate,
         @Param("endDate") ZonedDateTime endDate,
         Pageable pageable);

}