package com.verygana2.controllers.keys;

import com.verygana2.dtos.keys.KeyBalanceResponseDTO;
import com.verygana2.dtos.keys.SpendKeysRequestDTO;
import com.verygana2.dtos.keys.SpendKeysResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;


import com.verygana2.services.interfaces.finance.KeyWalletService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/consumer/wallet/keys")
@PreAuthorize("hasRole('ROLE_CONSUMER')")
@RequiredArgsConstructor
public class KeyWalletController {

    private final KeyWalletService keyWalletService;

    @GetMapping("/balance")
    public ResponseEntity<KeyBalanceResponseDTO> getBalance(
            @AuthenticationPrincipal Jwt jwt) {
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(keyWalletService.getBalance(consumerId));
    }

    @PostMapping("/spend")
    public ResponseEntity<SpendKeysResponseDTO> spend(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SpendKeysRequestDTO request) {
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(keyWalletService.spendKeysForPetGame(consumerId, request));
    }
}