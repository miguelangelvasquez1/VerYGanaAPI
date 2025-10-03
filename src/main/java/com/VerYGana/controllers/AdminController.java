package com.VerYGana.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.VerYGana.dtos2.admin2.responses2.AdminReportResponse;
import com.VerYGana.dtos2.wallet2.requests2.BlockBalanceRequest;
import com.VerYGana.dtos2.wallet2.requests2.UnblockBalanceRequest;
import com.VerYGana.services.interfaces.AdminService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin")
public class AdminController {
    
    @Autowired
    private AdminService adminService;

    // findByUserId
    // findByActionType


    @PostMapping("/block/balance")
    public ResponseEntity<AdminReportResponse> blockBalance (@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid BlockBalanceRequest request){
        Long userId = jwt.getClaim("userId");
        AdminReportResponse response = adminService.blockBalance(userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/unblock/balance")
    public ResponseEntity<AdminReportResponse> unblockBalance (@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid UnblockBalanceRequest request){
        Long userId = jwt.getClaim("userId");
        AdminReportResponse response = adminService.unblockBalance(userId, request);
        return ResponseEntity.ok(response);
    }
    

}
