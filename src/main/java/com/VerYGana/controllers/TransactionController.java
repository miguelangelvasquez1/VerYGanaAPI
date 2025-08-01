package com.VerYGana.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.VerYGana.models.Transaction;
import com.VerYGana.models.Enums.TransactionState;
import com.VerYGana.services.interfaces.TransactionService;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    
    @Autowired
    private TransactionService transactionService;

    // Obtener lista de transacciones por el id del usuario
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Transaction>> getByUserIdOrderByDateDesc(@PathVariable String userId){
        List<Transaction> foundTransactions = transactionService.getByUserIdOrderByDateDesc(userId);
        return ResponseEntity.ok(foundTransactions);
    }

    // Obtener transaccion por código de referencia
    @GetMapping("/reference/{referenceCode}")
    public ResponseEntity<Transaction> getByReferenceCode(@PathVariable String referenceCode){
        Transaction foundTransaction = transactionService.getByReferenceCode(referenceCode);
        return ResponseEntity.ok(foundTransaction);
    }

    // Obtener lista de transacciones por su estado
    @GetMapping("/state/{state}")
    public ResponseEntity<List<Transaction>> getByState (@PathVariable TransactionState state){
        List<Transaction> foundTransaction = transactionService.getByState(state);
        return ResponseEntity.ok(foundTransaction);
    }

    // Verificar si un codigo de referencia ya existe
    @GetMapping("/exists/referenceCode/{referenceCode}")
    public ResponseEntity<Boolean> existsByReferenceCode(@PathVariable String referenceCode){
        return ResponseEntity.ok(transactionService.existsByReferenceCode(referenceCode));
    }
}
