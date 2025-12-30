package com.verygana2.services;


import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.PagedResponse;
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
         throw new IllegalArgumentException("UserId null or not valid");
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
         throw new IllegalArgumentException("UserId null or not valid");
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
         throw new IllegalArgumentException("UserId null or not valid");
      }
      Long walletId = walletService.getByOwnerId(userId).getId();
      return PagedResponse.from(transactionRepository.findByWalletId(walletId, pageable));
   }

   @Override
   public PagedResponse<TransactionResponseDTO> getByReferenceId(Long userId, String referenceId, Pageable pageable) {
      if (userId == null || userId <= 0) {
         throw new IllegalArgumentException("UserId must be positive");
      }

      if (referenceId == null || referenceId.isBlank()) {
         throw new IllegalArgumentException("ReferenceId null or not valid");
      }
      return PagedResponse.from(transactionRepository.findByReferenceId(userId, referenceId, pageable));
   }

   @Override
   public Long countByWalletIdAndTransactionType(Long userId, TransactionType transactionType) {
      if (userId == null || userId <= 0) {
         throw new IllegalArgumentException("UserId null or not valid");
      }
      if (transactionType == null) {
         throw new IllegalArgumentException("TransactionType cannot be null");
      }
      
      Long walletId = walletService.getByOwnerId(userId).getId();
      return transactionRepository.countByWalletIdAndTransactionType(walletId, transactionType);
   }

   @Override
   public Long getTotalConsumerEarnings(Long consumerId) {
      if (consumerId == null || consumerId <= 0) {
         throw new IllegalArgumentException("UserId null or not valid");
      }
      Long walletId = walletService.getByOwnerId(consumerId).getId();
      Long earnings = (transactionRepository.sumUserEarningsByWalletId(walletId) != null) ? transactionRepository.sumUserEarningsByWalletId(walletId): 0L;
      return earnings;
   }

}
