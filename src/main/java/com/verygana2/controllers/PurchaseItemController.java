package com.verygana2.controllers;


import org.springframework.data.domain.Pageable;
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
import com.verygana2.dtos.product.responses.FeaturedProductResponseDTO;
import com.verygana2.services.interfaces.PurchaseItemService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/purchaseItems")
@RequiredArgsConstructor
public class PurchaseItemController {
    
    private final PurchaseItemService purchaseItemService;

    @GetMapping("/totalSales")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<Long> getTotalCommercialSales(@AuthenticationPrincipal Jwt jwt){
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(purchaseItemService.getTotalSalesbyCommercial(commercialId));
    }

    @GetMapping("/totalSales/monthly")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<Integer> getTotalCommercialSalesByMonth(@AuthenticationPrincipal Jwt jwt, @RequestParam Integer year, @RequestParam Integer month){
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(purchaseItemService.getTotalCommercialSalesByMonth(commercialId, year, month));
    }

    @GetMapping("/topSelling")
    @PreAuthorize("hasRole('ROLE_COMMERCIAL')")
    public ResponseEntity<PagedResponse<FeaturedProductResponseDTO>> getTopSellingProductsPage (@AuthenticationPrincipal Jwt jwt, @PageableDefault(size = 5, page = 0) Pageable pageable){
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(purchaseItemService.getTopSellingProductsPage(commercialId, pageable));
    }
}