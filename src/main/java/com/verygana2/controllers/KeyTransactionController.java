package com.verygana2.controllers;

import java.time.ZonedDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.finance.responses.KeyTransactionResponseDTO;
import com.verygana2.models.enums.finance.KeyTransactionType;
import com.verygana2.services.interfaces.finance.KeyTransactionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/key-transactions")
@PreAuthorize("hasRole('ROLE_CONSUMER')")
@RequiredArgsConstructor
public class KeyTransactionController {

    private final KeyTransactionService keyTransactionService;

    @GetMapping
    public ResponseEntity<PagedResponse<KeyTransactionResponseDTO>> getMyTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "initialDate", required = false) ZonedDateTime initialDate,
            @RequestParam(name = "endDate", required = false) ZonedDateTime endDate,
            @RequestParam(name = "type", required = false) KeyTransactionType type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(PagedResponse.from(keyTransactionService.getByConsumerId(consumerId,initialDate, endDate, type, pageable)));
    }

    @GetMapping("/total-earned-keys")
    public ResponseEntity<Long> getTotalEarnedKeys (@AuthenticationPrincipal Jwt jwt){
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(keyTransactionService.getTotalEarnedKeys(consumerId));
    }

    @GetMapping("/total-used-keys")
    public ResponseEntity<Long> getTotalUsedKeys (@AuthenticationPrincipal Jwt jwt){
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(keyTransactionService.getTotalUsedKeys(consumerId));
    }

    @GetMapping("/total-expired-keys")
    public ResponseEntity<Long> getTotalExpiredKeys (@AuthenticationPrincipal Jwt jwt){
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(keyTransactionService.getTotalExpiredKeys(consumerId));
    }
}
