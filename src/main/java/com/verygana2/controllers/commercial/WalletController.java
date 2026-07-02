package com.verygana2.controllers.commercial;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.wallet.responses.BillingSummaryResponseDTO;
import com.verygana2.dtos.wallet.responses.DepositResponseDTO;
import com.verygana2.dtos.wallet.responses.PayoutSummaryResponseDTO;
import com.verygana2.services.interfaces.finance.WalletService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/commercial/wallet")
@PreAuthorize("hasRole('ROLE_COMMERCIAL')")
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me/billing-summary")
    public ResponseEntity<BillingSummaryResponseDTO> getBillingSummary(
            @AuthenticationPrincipal Jwt jwt) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(walletService.getBillingSummary(commercialId));
    }

    @GetMapping("/me/deposits")
    public ResponseEntity<PagedResponse<DepositResponseDTO>> getDeposits(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam int year,
            @RequestParam int month,
            Pageable pageable) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(walletService.getDeposits(commercialId, year, month, pageable));
    }

    @GetMapping("/me/payouts")
    public ResponseEntity<PagedResponse<PayoutSummaryResponseDTO>> getPayouts(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam int year,
            @RequestParam int month,
            Pageable pageable) {
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(walletService.getPayouts(commercialId, year, month, pageable));
    }
}
