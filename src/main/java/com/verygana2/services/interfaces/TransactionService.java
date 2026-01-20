package com.verygana2.services.interfaces;



import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.seller.responses.EarningsByMonthResponseDTO;
import com.verygana2.dtos.transaction.responses.TransactionPayoutResponseDTO;
import com.verygana2.dtos.transaction.responses.TransactionResponseDTO;
import com.verygana2.models.enums.TransactionState;
import com.verygana2.models.enums.TransactionType;

public interface TransactionService {
     PagedResponse<TransactionResponseDTO> getByWalletIdAndTransactionType(Long userId, TransactionType transactionType, Pageable pageable);
     PagedResponse<TransactionResponseDTO> getByWalletIdAndTransactionState(Long userId, TransactionState transactionState, Pageable pageable);
     PagedResponse<TransactionResponseDTO> getByWalletId(Long userId, Pageable pageable);
     PagedResponse<TransactionResponseDTO> getByReferenceId(Long userId, String referenceId, Pageable pageable);
     Long countByWalletIdAndTransactionType(Long userId, TransactionType transactionType);
     BigDecimal getTotalConsumerEarningsAmount (Long consumerId);
     BigDecimal getTotalSellerEarningsAmount (Long sellerId);
     List<EarningsByMonthResponseDTO> getSellerEarningsByYearList(Long sellerId, Integer year);
     BigDecimal getSellerEarningsByMonth(Long sellerId, Integer year, Integer month);
     PagedResponse<TransactionPayoutResponseDTO> getSellerPayoutsPage (Long sellerId, Integer year, Integer month, Pageable pageable);
}
