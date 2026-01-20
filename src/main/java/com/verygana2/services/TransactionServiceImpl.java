package com.verygana2.services;


import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.seller.responses.EarningsByMonthResponseDTO;
import com.verygana2.dtos.transaction.responses.TransactionPayoutResponseDTO;
import com.verygana2.dtos.transaction.responses.TransactionResponseDTO;
import com.verygana2.models.enums.TransactionState;
import com.verygana2.models.enums.TransactionType;
import com.verygana2.repositories.TransactionRepository;
import com.verygana2.services.interfaces.TransactionService;
import com.verygana2.services.interfaces.WalletService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {
   
   private final TransactionRepository transactionRepository;
   private final WalletService walletService;

   @Override
   public PagedResponse<TransactionResponseDTO> getByWalletIdAndTransactionType(Long userId, TransactionType transactionType, Pageable pageable) {
      if (userId == null || userId <= 0) {
         throw new IllegalArgumentException("User id null or not valid");
      }
      if (transactionType == null) {
         throw new IllegalArgumentException("TransactionType cannot be null");
      }

      Long walletId = walletService.getByOwnerId(userId).getId();
      return PagedResponse.from(transactionRepository.findByWalletIdAndTransactionType(walletId, transactionType, pageable));
   }

   @Override
   public PagedResponse<TransactionResponseDTO> getByWalletIdAndTransactionState(Long userId, TransactionState transactionState, Pageable pageable) {
      if (userId == null || userId <= 0) {
         throw new IllegalArgumentException("User id null or not valid");
      }
      if (transactionState == null) {
         throw new IllegalArgumentException("TransactionState cannot be null");
      }
      Long walletId = walletService.getByOwnerId(userId).getId();
      return PagedResponse.from(transactionRepository.findByWalletIdAndTransactionState(walletId, transactionState, pageable));
   }

   @Override
   public PagedResponse<TransactionResponseDTO> getByWalletId(Long userId, Pageable pageable) {
      if (userId == null || userId <= 0) {
         throw new IllegalArgumentException("User id null or not valid");
      }
      Long walletId = walletService.getByOwnerId(userId).getId();
      return PagedResponse.from(transactionRepository.findByWalletId(walletId, pageable));
   }

   @Override
   public PagedResponse<TransactionResponseDTO> getByReferenceId(Long userId, String referenceId, Pageable pageable) {
      if (userId == null || userId <= 0) {
         throw new IllegalArgumentException("User id must be positive");
      }

      if (referenceId == null || referenceId.isBlank()) {
         throw new IllegalArgumentException("Reference id null or not valid");
      }
      return PagedResponse.from(transactionRepository.findByReferenceId(userId, referenceId, pageable));
   }

   @Override
   public Long countByWalletIdAndTransactionType(Long userId, TransactionType transactionType) {
      if (userId == null || userId <= 0) {
         throw new IllegalArgumentException("User id null or not valid");
      }
      if (transactionType == null) {
         throw new IllegalArgumentException("TransactionType cannot be null");
      }
      
      Long walletId = walletService.getByOwnerId(userId).getId();
      return transactionRepository.countByWalletIdAndTransactionType(walletId, transactionType);
   }

   @Override
   public BigDecimal getTotalConsumerEarningsAmount(Long consumerId) {
      if (consumerId == null || consumerId <= 0) {
         throw new IllegalArgumentException("User id null or not valid");
      }
      Long walletId = walletService.getByOwnerId(consumerId).getId();
      BigDecimal earnings = (transactionRepository.sumUserEarningsByWalletId(walletId) != null) ? transactionRepository.sumUserEarningsByWalletId(walletId): BigDecimal.ZERO;
      return earnings;
   }

   @Override
   public BigDecimal getTotalSellerEarningsAmount(Long sellerId) {
      if (sellerId == null || sellerId <= 0) {
         throw new IllegalArgumentException("Seller id must be positive");
      }
      return transactionRepository.sumTotalSellerEarningsAmount(sellerId);
   }

   @Override
   public List<EarningsByMonthResponseDTO> getSellerEarningsByYearList(Long sellerId, Integer year) {
      if (sellerId == null || sellerId <= 0) {
         throw new IllegalArgumentException("Seller id must be positive");
      }
      if (year == null || year <= 0) {
         throw new IllegalArgumentException("Year must be positive");
      }
      return transactionRepository.findSellerEarningsByYear(sellerId, year);
   }

   @Override
   public BigDecimal getSellerEarningsByMonth(Long sellerId, Integer year, Integer month) {
      if (sellerId == null || sellerId <= 0) {
         throw new IllegalArgumentException("Seller id must be positive");
      }
      if (year == null || year <= 0) {
         throw new IllegalArgumentException("Year must be positive");
      }

      if (month == null || month <= 0 || month > 12) {
         throw new IllegalArgumentException("Month must be between 1 and 12");
      }

      ZonedDateTime startDate = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneId.of("America/Bogota"));
      ZonedDateTime endDate = startDate.plusMonths(1);
      return transactionRepository.findSellerEarningsByMonth(sellerId, startDate, endDate);
   }

   @Override
   public PagedResponse<TransactionPayoutResponseDTO> getSellerPayoutsPage(Long sellerId, Integer year, Integer month, Pageable pageable) {
      
      if (sellerId == null || sellerId <= 0) {
         throw new IllegalArgumentException("Seller id must be positive");
      }
      if (year == null || year <= 0) {
         throw new IllegalArgumentException("Year must be positive");
      }

      if (month == null || month <= 0 || month > 12) {
         throw new IllegalArgumentException("Month must be between 1 and 12");
      }

      ZonedDateTime startDate = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneId.of("America/Bogota"));
      ZonedDateTime endDate = startDate.plusMonths(1);
      Page<TransactionPayoutResponseDTO> payoutsPage = transactionRepository.findSellerPayouts(sellerId, startDate, endDate, pageable);
      
      return PagedResponse.from(payoutsPage);

   }

}
