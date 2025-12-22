package com.verygana2.controllers;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.models.products.Purchase;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.PurchaseService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/emails")
@RequiredArgsConstructor
public class EmailController {
    
    private final EmailService emailService;
    private final PurchaseService purchaseService;

    @PostMapping("/send")
    public ResponseEntity<EntityCreatedResponseDTO> sendEmailTryOut (){
        Purchase purchase = purchaseService.getPurchaseById(10L);
        String contactEmail = "juanparodriguezg@gmail.com";
        emailService.sendPurchaseConfirmation(purchase, contactEmail);
        emailService.sendSellerSaleNotification(purchase);
        return ResponseEntity.ok(new EntityCreatedResponseDTO(purchase.getId(), "email sent succesfully", Instant.now()));
    }
}
