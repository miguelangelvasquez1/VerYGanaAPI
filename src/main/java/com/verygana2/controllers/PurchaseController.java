package com.verygana2.controllers;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponse;
import com.verygana2.dtos.purchase.requests.CreatePurchaseRequestDTO;
import com.verygana2.models.Transaction;
import com.verygana2.models.products.Purchase;
import com.verygana2.services.interfaces.PurchaseService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @PostMapping("/buy")
    @PreAuthorize("hasAuthority('ROLE_CONSUMER')")
    public ResponseEntity<EntityCreatedResponse> createPurchase(@AuthenticationPrincipal Jwt jwt,
            @RequestBody CreatePurchaseRequestDTO request) {
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(purchaseService.createPurchase(consumerId, request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_CONSUMER')")
    public ResponseEntity<PagedResponse<Purchase>> getPurchases(@AuthenticationPrincipal Jwt jwt, Pageable pageable) {
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(purchaseService.getConsumerPurchases(consumerId, pageable));
    }

    @GetMapping("/{purchaseId}/transactions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<Transaction>> getPurchaseTransactions(@PathVariable Long purchaseId) {
        return ResponseEntity.ok(purchaseService.getPurchaseTransactions(purchaseId));
    }

}
