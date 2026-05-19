package com.verygana2.services.interfaces.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.wallet.requests.DepositRequest;
import com.verygana2.dtos.wallet.requests.WithdrawalRequest;
import com.verygana2.dtos.wallet.responses.DepositInitiatedResponse;
import com.verygana2.dtos.wallet.responses.PayoutSummaryResponse;
import com.verygana2.dtos.wallet.responses.TransactionResponse;
import com.verygana2.dtos.wallet.responses.WalletResponse;
import com.verygana2.models.finance.Wallet;

public interface WalletService {

    Wallet createFor(Long commercialId);
    Wallet getByCommercialId(Long commercialId);

    WalletResponse getMyWallet(Long commercialId);
    DepositInitiatedResponse initiateDeposit(Long commercialId, DepositRequest request);
    TransactionResponse requestWithdrawal(Long commercialId, WithdrawalRequest request);
    Page<TransactionResponse> getTransactions(Long commercialId, Pageable pageable);
    Page<PayoutSummaryResponse> getPayouts(Long commercialId, Pageable pageable);
}
