package com.verygana2.controllers;


import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.CategoryRequestDTO;
import com.verygana2.dtos.admin.responses.AdminReportResponse;
import com.verygana2.dtos.generic.EntityCreatedResponse;
import com.verygana2.dtos.product.requests.CreateProductCategoryRequest;
import com.verygana2.dtos.wallet.requests.BlockBalanceRequest;
import com.verygana2.dtos.wallet.requests.UnblockBalanceRequest;
import com.verygana2.services.interfaces.AdminService;
import com.verygana2.services.interfaces.CategoryService;
import com.verygana2.services.interfaces.ProductCategoryService;
import com.verygana2.utils.Locations.LocationImportService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    
    private final AdminService adminService;
    private final ProductCategoryService productCategoryService;
    private final CategoryService categoryService;

    private final LocationImportService importService;

    // findByUserId
    // findByActionType


    @GetMapping("/import-locations")
    public String importLocations() {
        importService.importFromDane();
        return "Importación finalizada";
    }

    @PostMapping("/block/balance")
    //@PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<AdminReportResponse> blockBalance (@RequestBody @Valid BlockBalanceRequest request){
        AdminReportResponse response = adminService.blockBalance(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/unblock/balance")
    //@PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<AdminReportResponse> unblockBalance (@RequestBody @Valid UnblockBalanceRequest request){
        AdminReportResponse response = adminService.unblockBalance(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/create/productCategory")
    //@PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<EntityCreatedResponse> createProductCategory(@RequestBody @Valid CreateProductCategoryRequest request){
        EntityCreatedResponse response = productCategoryService.create(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/create/category")
    //@PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<EntityCreatedResponse> createCategory(@RequestBody @Valid CategoryRequestDTO request){
        EntityCreatedResponse response = categoryService.create(request);
        return ResponseEntity.created(URI.create("/categories")).body(response);
    }

}
