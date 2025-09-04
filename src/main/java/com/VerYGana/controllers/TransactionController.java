package com.VerYGana.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.VerYGana.models.Transaction;
import com.VerYGana.services.interfaces.TransactionService;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    
    @Autowired
    private TransactionService transactionService;

    // Obtener lista de transacciones por el id de la billetera
    @GetMapping("/wallet/{walletId}")
    public ResponseEntity<List<Transaction>> getByUserIdOrderByDateDesc(@PathVariable Long walletId){
        List<Transaction> foundTransactions = transactionService.getByWalletId(walletId);
        return ResponseEntity.ok(foundTransactions);
    }

    // Obtener transacción por código de referencia
    @GetMapping("/reference/{referenceCode}")
    public ResponseEntity<Transaction> getByReferenceCode(@PathVariable String referenceCode){
        Transaction foundTransaction = transactionService.getByReferenceId(referenceCode);
        return ResponseEntity.ok(foundTransaction);
    }

    // Verificar si un código de referencia ya existe
    @GetMapping("/exists/referenceCode/{referenceCode}")
    public ResponseEntity<Boolean> existsByReferenceCode(@PathVariable String referenceCode){
        boolean exists = transactionService.existsByReferenceId(referenceCode);
        return ResponseEntity.ok(exists);
    }
}

