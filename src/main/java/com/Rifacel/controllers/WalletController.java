package com.Rifacel.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Rifacel.models.Wallet;
import com.Rifacel.services.interfaces.WalletService;

@RestController
@RequestMapping("/wallets")
public class WalletController {
 
    @Autowired
    private WalletService walletService;

    @GetMapping("/{userId}")
    public ResponseEntity<Wallet> getWalletByUserId (@PathVariable String userId){
        Wallet foundWallet = walletService.getWalletByUserId(userId);
        return ResponseEntity.ok(foundWallet); 
    }
}
