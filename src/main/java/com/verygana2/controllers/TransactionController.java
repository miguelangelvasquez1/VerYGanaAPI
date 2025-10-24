package com.verygana2.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.models.Transaction;
import com.verygana2.services.interfaces.TransactionService;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    
    @Autowired
    private TransactionService transactionService;

    // Obtener lista de transacciones por el id de la billetera
    @GetMapping("/wallet/{walletId}")
    public ResponseEntity<List<Transaction>> getByUserIdOrderByDateDesc(@PathVariable Long walletId){
        return ResponseEntity.ok(transactionService.getByWalletId(walletId));
    }

    // Obtener transacción por código de referencia
    @GetMapping("/reference/{referenceCode}")
    public ResponseEntity<List<Transaction>> getByReferenceCode(@PathVariable String referenceCode){
        return ResponseEntity.ok(transactionService.getByReferenceId(referenceCode));
    }

    // Verificar si un código de referencia ya existe
    @GetMapping("/exists/referenceCode/{referenceCode}")
    public ResponseEntity<Boolean> existsByReferenceCode(@PathVariable String referenceCode){
        return ResponseEntity.ok(transactionService.existsByReferenceId(referenceCode));
    }
}

