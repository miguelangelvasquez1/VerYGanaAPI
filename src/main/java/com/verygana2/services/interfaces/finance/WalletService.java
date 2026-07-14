package com.verygana2.services.interfaces.finance;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.wallet.responses.BillingSummaryResponseDTO;
import com.verygana2.dtos.wallet.responses.DepositResponseDTO;
import com.verygana2.dtos.wallet.responses.PayoutSummaryResponseDTO;
import com.verygana2.models.finance.Wallet;

public interface WalletService {

    Wallet createFor(Long commercialId);

    BillingSummaryResponseDTO getBillingSummary(Long commercialId);

    PagedResponse<DepositResponseDTO> getDeposits(Long commercialId, int year, int month, Pageable pageable);

    PagedResponse<PayoutSummaryResponseDTO> getPayouts(Long commercialId, int year, int month, Pageable pageable);
}
