package com.verygana2.controllers;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.seller.responses.EarningsByMonthResponseDTO;
import com.verygana2.dtos.transaction.responses.TransactionPayoutResponseDTO;
import com.verygana2.dtos.transaction.responses.TransactionResponseDTO;
import com.verygana2.models.enums.TransactionState;
import com.verygana2.models.enums.TransactionType;
import com.verygana2.services.interfaces.TransactionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {
    
    private final TransactionService transactionService;

    // Obtener lista de transacciones por el id de la billetera
    @GetMapping
    public ResponseEntity<PagedResponse<TransactionResponseDTO>> getByWalletId(@AuthenticationPrincipal Jwt jwt, @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable){
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(transactionService.getByWalletId(userId, pageable));
    }

    // Obtener lista de transacciones por tipo
    @GetMapping("/type/{transactionType}")
    public ResponseEntity<PagedResponse<TransactionResponseDTO>> getByTransactionType (@AuthenticationPrincipal Jwt jwt, @PathVariable TransactionType transactionType, @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable){
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(transactionService.getByWalletIdAndTransactionType(userId, transactionType, pageable));
    }

    // Obtener lista de transacciones por estado
    @GetMapping("/state/{transactionState}")
    public ResponseEntity<PagedResponse<TransactionResponseDTO>> getByTransactionState (@AuthenticationPrincipal Jwt jwt, @PathVariable TransactionState transactionState, @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable){
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(transactionService.getByWalletIdAndTransactionState(userId, transactionState, pageable));
    }

    // Obtener cantidad de depositos, retiros etc. que haya hecho un usuario
    @GetMapping("/count")
    public ResponseEntity<Long> countTransactionsByType(@AuthenticationPrincipal Jwt jwt, @RequestParam TransactionType transactionType){
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(transactionService.countByWalletIdAndTransactionType(userId, transactionType));
    }

    // Obtener las ganancias totales que haya tenido un usuario
    @GetMapping("/earnings")
    public ResponseEntity<BigDecimal> getConsumerEarnings (@AuthenticationPrincipal Jwt jwt){
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(transactionService.getTotalConsumerEarningsAmount(consumerId));
    }

    // Obtener transacción por código de referencia
    @GetMapping("/reference")
    public ResponseEntity<PagedResponse<TransactionResponseDTO>> getByReferenceCode(@AuthenticationPrincipal Jwt jwt, @RequestParam String code, @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable){
        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(transactionService.getByReferenceId(userId, code, pageable));
    }

    @GetMapping("/earnings/anually")
    @PreAuthorize("hasRole('ROLE_SELLER')")
    public ResponseEntity<List<EarningsByMonthResponseDTO>> getEarningsByYearList (@AuthenticationPrincipal Jwt jwt, @RequestParam Integer year){
        Long sellerId = jwt.getClaim("userId");
        return ResponseEntity.ok(transactionService.getSellerEarningsByYearList(sellerId, year));
    }

    @GetMapping("/earnings/monthly")
    @PreAuthorize("hasRole('ROLE_SELLER')")
    public ResponseEntity<BigDecimal> getEarningsByMonth (@AuthenticationPrincipal Jwt jwt, @RequestParam Integer year, @RequestParam Integer month){
        Long sellerId = jwt.getClaim("userId");
        return ResponseEntity.ok(transactionService.getSellerEarningsByMonth(sellerId, year, month));
    }

    @GetMapping("/payouts")
    @PreAuthorize("hasRole('ROLE_SELLER')")
    public ResponseEntity<PagedResponse<TransactionPayoutResponseDTO>> getSellerPayoutsPage (@AuthenticationPrincipal Jwt jwt, @RequestParam Integer year, @RequestParam Integer month, @PageableDefault(size = 5, page = 0) Pageable pageable){
        Long sellerId = jwt.getClaim("userId");
        return ResponseEntity.ok(transactionService.getSellerPayoutsPage(sellerId, year, month, pageable));
    }

}

