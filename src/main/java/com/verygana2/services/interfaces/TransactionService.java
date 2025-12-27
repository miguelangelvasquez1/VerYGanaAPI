package com.verygana2.services.interfaces;



import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.transaction.responses.TransactionResponseDTO;
import com.verygana2.models.enums.TransactionState;
import com.verygana2.models.enums.TransactionType;

public interface TransactionService {
     PagedResponse<TransactionResponseDTO> getByWalletIdAndTransactionType(Long userId, TransactionType transactionType, Pageable pageable);
     PagedResponse<TransactionResponseDTO> getByWalletIdAndTransactionState(Long userId, TransactionState transactionState, Pageable pageable);
     PagedResponse<TransactionResponseDTO> getByWalletId(Long userId, Pageable pageable);
     PagedResponse<TransactionResponseDTO> getByReferenceId(String referenceId, Pageable pageable);
     Long countByWalletIdAndTransactionType(Long userId, TransactionType transactionType);
     Long getTotalConsumerEarnings (Long consumerId);
}
