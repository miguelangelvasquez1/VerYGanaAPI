package com.verygana2.controllers;

import java.math.BigDecimal;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.services.interfaces.PlatformTreasuryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/treasury")
@RequiredArgsConstructor
public class PlatformTreasuryController {

    private final PlatformTreasuryService platformTreasuryService;


    @GetMapping("/balance")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<BigDecimal> getTotalBalance(){
        return ResponseEntity.ok(platformTreasuryService.getTotalBalance());
    }

    @GetMapping("/balance/available")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<BigDecimal> getAvailableBalance(){
        return ResponseEntity.ok(platformTreasuryService.getAvailableBalance());
    }
    @GetMapping("/balance/reserved")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<BigDecimal> getReservedBalance(){
        return ResponseEntity.ok(platformTreasuryService.getReservedBalance());
    }
    
}
