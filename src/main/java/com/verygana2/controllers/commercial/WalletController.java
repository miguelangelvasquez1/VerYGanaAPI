package com.verygana2.controllers.commercial;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.wallet.requests.DepositRequest;
import com.verygana2.dtos.wallet.requests.WithdrawalRequest;
import com.verygana2.dtos.wallet.responses.DepositInitiatedResponse;
import com.verygana2.dtos.wallet.responses.PayoutSummaryResponse;
import com.verygana2.dtos.wallet.responses.TransactionResponse;
import com.verygana2.dtos.wallet.responses.WalletResponse;
import com.verygana2.services.interfaces.finance.WalletService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/commercial/wallet")
@PreAuthorize("hasRole('ROLE_COMMERCIAL')")
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me")
    public ResponseEntity<WalletResponse> getMyWallet(@AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(walletService.getMyWallet(commercialId));
    }

    @PostMapping("/deposit")
    public ResponseEntity<DepositInitiatedResponse> initiateDeposit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid DepositRequest request) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(walletService.initiateDeposit(commercialId, request));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> requestWithdrawal(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid WithdrawalRequest request) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(walletService.requestWithdrawal(commercialId, request));
    }

    @GetMapping("/me/transactions")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @AuthenticationPrincipal Jwt jwt,
            Pageable pageable) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(walletService.getTransactions(commercialId, pageable));
    }

    @GetMapping("/me/payouts")
    public ResponseEntity<Page<PayoutSummaryResponse>> getPayouts(
            @AuthenticationPrincipal Jwt jwt,
            Pageable pageable) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(walletService.getPayouts(commercialId, pageable));
    }
}
