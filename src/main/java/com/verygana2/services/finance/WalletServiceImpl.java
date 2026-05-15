package com.verygana2.services.finance;

import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.wallet.requests.DepositRequest;
import com.verygana2.dtos.wallet.requests.WithdrawalRequest;
import com.verygana2.dtos.wallet.responses.DepositInitiatedResponse;
import com.verygana2.dtos.wallet.responses.PayoutSummaryResponse;
import com.verygana2.dtos.wallet.responses.TransactionResponse;
import com.verygana2.dtos.wallet.responses.WalletResponse;
import com.verygana2.models.finance.Wallet;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.services.interfaces.details.CommercialDetailsService;
import com.verygana2.services.interfaces.finance.WalletService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final CommercialDetailsService commercialDetailsService;

    @Override
    public void createFor(Long commercialId) {

        if (!walletRepository.existsByCommercialId(commercialId)) {
            walletRepository.save(
                    Objects.requireNonNull(Wallet.createFor(commercialDetailsService.getCommercialById(commercialId))));
        }
    }

    @Override
    public Wallet getByCommercialId(Long commercialId) {

        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }

        return walletRepository.findByCommercialId(commercialId).orElseThrow(() -> new EntityNotFoundException("Commercial with id: " + commercialId + " not found "));
                
    }

    @Override
    public WalletResponse getMyWallet(Long commercialId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMyWallet'");
    }

    @Override
    public DepositInitiatedResponse initiateDeposit(Long commercialId, DepositRequest request) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'initiateDeposit'");
    }

    @Override
    public TransactionResponse requestWithdrawal(Long commercialId, WithdrawalRequest request) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'requestWithdrawal'");
    }

    @Override
    public Page<TransactionResponse> getTransactions(Long commercialId, Pageable pageable) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTransactions'");
    }

    @Override
    public Page<PayoutSummaryResponse> getPayouts(Long commercialId, Pageable pageable) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPayouts'");
    }

}
