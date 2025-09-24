package com.VerYGana.controllers;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.VerYGana.dtos.Wallet.Requests.DepositRequest;
import com.VerYGana.dtos.Wallet.Requests.TransferRequest;
import com.VerYGana.dtos.Wallet.Requests.WithdrawalRequest;
import com.VerYGana.dtos.Wallet.Responses.TransactionResponse;
import com.VerYGana.dtos.Wallet.Responses.WalletResponse;
import com.VerYGana.services.interfaces.WalletService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/wallets")
public class WalletController {

    @Autowired
    private WalletService walletService;

    // These methods uses GlobalExceptionHandler

    @GetMapping
    public ResponseEntity<WalletResponse> getWalletByOwnerId (@AuthenticationPrincipal Jwt jwt){
        Long userId = jwt.getClaim("userId");
        WalletResponse response = walletService.getWalletByOwnerId(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> doDeposit (@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid DepositRequest request){
        Long userId = jwt.getClaim("userId");
        TransactionResponse response = walletService.doDeposit(userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> doWithdrawal (@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid WithdrawalRequest request){
        Long userId = jwt.getClaim("userId");
        TransactionResponse response = walletService.doWithdrawal(userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transferToUser (@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid TransferRequest request){
        Long userId = jwt.getClaim("userId");
        TransactionResponse response = walletService.transferToUser(userId, request);
        return ResponseEntity.ok(response);
    }

    // Balance Queries

    @GetMapping("/balance")
    public ResponseEntity<BigDecimal> getAvailableBalance (@AuthenticationPrincipal Jwt jwt){
        Long userId = jwt.getClaim("userId");
        BigDecimal availableBalance = walletService.getAvailableBalance(userId); 
        return ResponseEntity.ok(availableBalance);
    }

    @GetMapping("/balance/blocked")
    public ResponseEntity<BigDecimal> getBlockedBalance (@AuthenticationPrincipal Jwt jwt){
        Long userId = jwt.getClaim("userId");
        BigDecimal blockedBalance = walletService.getBlockedBalance(userId); 
        return ResponseEntity.ok(blockedBalance);
    }

    

}
