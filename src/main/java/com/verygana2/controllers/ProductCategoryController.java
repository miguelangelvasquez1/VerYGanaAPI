package com.verygana2.controllers;

import java.net.URI;
import java.util.List;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.generic.EntityCreatedResponse;
import com.verygana2.dtos.product.requests.CreateProductCategoryRequest;
import com.verygana2.dtos.product.responses.ProductCategoryResponseDTO;
import com.verygana2.services.interfaces.ProductCategoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/productCategories")
@RequiredArgsConstructor
public class ProductCategoryController {
    
    private final ProductCategoryService productCategoryService;

    @PostMapping("/create")
    //@PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<EntityCreatedResponse> createProductCategory (@Valid @RequestBody CreateProductCategoryRequest request){
        return ResponseEntity.created(Objects.requireNonNull(URI.create("/productCategories"))).body(productCategoryService.create(request));
    } 

    @DeleteMapping("/delete/{id}")
    //@PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteProductCategory(@PathVariable Long id){
        productCategoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    //@GetMapping("/{id}")
    //public ResponseEntity<ProductCategoryResponseDTO> getById (@PathVariable Long id){

    //}

    @GetMapping
    public ResponseEntity<List<ProductCategoryResponseDTO>> getProductCategories (){
        return ResponseEntity.ok(productCategoryService.getProductCategories());
    }
}
