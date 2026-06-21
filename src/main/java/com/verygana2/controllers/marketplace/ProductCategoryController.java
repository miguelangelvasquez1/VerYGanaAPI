package com.verygana2.controllers.marketplace;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.product.responses.ProductCategoryResponseDTO;
import com.verygana2.services.interfaces.marketplace.ProductCategoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/productCategories")
@RequiredArgsConstructor
public class ProductCategoryController {
    
    private final ProductCategoryService productCategoryService;

    @GetMapping
    public ResponseEntity<List<ProductCategoryResponseDTO>> getActiveProductCategories (){
        return ResponseEntity.ok(productCategoryService.getActiveProductCategories());
    }
}
