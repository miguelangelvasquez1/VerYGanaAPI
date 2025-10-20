package com.verygana2.controllers;


import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.admin.responses.AdminReportResponse;
import com.verygana2.dtos.generic.EntityCreatedResponse;
import com.verygana2.dtos.products.requests.CreateProductCategoryRequest;
import com.verygana2.dtos.wallet.requests.BlockBalanceRequest;
import com.verygana2.dtos.wallet.requests.UnblockBalanceRequest;
import com.verygana2.services.interfaces.AdminService;
import com.verygana2.services.interfaces.ProductCategoryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin")
public class AdminController {
    
    private final AdminService adminService;
    private final ProductCategoryService productCategoryService;

    public AdminController(AdminService adminService, ProductCategoryService productCategoryService){
        this.adminService = adminService;
        this.productCategoryService = productCategoryService;
    }

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

    @PostMapping("/create/productCategory")
    //@PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<EntityCreatedResponse> createProductCategory(@RequestBody CreateProductCategoryRequest request){
        EntityCreatedResponse response = productCategoryService.create(request);
        return ResponseEntity.ok(response);
    }
    

}
