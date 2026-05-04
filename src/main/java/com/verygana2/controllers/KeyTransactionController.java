package com.verygana2.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.finance.responses.KeyTransactionResponseDTO;
import com.verygana2.models.enums.finance.KeyTransactionType;
import com.verygana2.services.interfaces.finance.KeyTransactionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/consumer/transactions")
@PreAuthorize("hasRole('ROLE_CONSUMER')")
@RequiredArgsConstructor
public class KeyTransactionController {

    private final KeyTransactionService keyTransactionService;

    @GetMapping
    public ResponseEntity<Page<KeyTransactionResponseDTO>> getMyTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(keyTransactionService.getByConsumerId(consumerId, pageable));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<Page<KeyTransactionResponseDTO>> getMyTransactionsByType(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable KeyTransactionType type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(keyTransactionService.getByConsumerIdAndType(consumerId, type, pageable));
    }
}
