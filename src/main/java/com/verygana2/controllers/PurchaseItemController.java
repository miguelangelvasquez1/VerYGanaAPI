package com.verygana2.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.services.interfaces.PurchaseItemService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/purchaseItems")
@RequiredArgsConstructor
public class PurchaseItemController {
    
    private final PurchaseItemService purchaseItemService;

    @GetMapping("/TotalSales")
    public ResponseEntity<Long> getTotalSellerSales(@AuthenticationPrincipal Jwt jwt){
        Long sellerId = jwt.getClaim("sellerId");
        return ResponseEntity.ok(purchaseItemService.getTotalSalesbySeller(sellerId));
    }
}
