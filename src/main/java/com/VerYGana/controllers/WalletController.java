package com.VerYGana.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.VerYGana.models.Wallet;
import com.VerYGana.services.interfaces.WalletService;

@RestController
@RequestMapping("/wallets")
public class WalletController {
 
    @Autowired
    private WalletService walletService;

    // Obtener la billetera del usuario pasando su id como argumento
    @GetMapping("/{userId}")
    public ResponseEntity<Wallet> getWalletByUserId (@PathVariable String userId){
        Wallet foundWallet = walletService.getWalletByUserId(userId);
        return ResponseEntity.ok(foundWallet); 
    }
}
