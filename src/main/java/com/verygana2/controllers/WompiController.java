package com.verygana2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.verygana2.dtos.wompi.WompiDepositRequest;
import com.verygana2.dtos.wompi.WompiDepositResponse;
import com.verygana2.services.interfaces.WompiPaymentService;
import com.verygana2.services.interfaces.WompiService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller para operaciones de pago con Wompi
 */
@RestController
@RequestMapping("/api/payments/wompi")
@RequiredArgsConstructor
@Slf4j
public class WompiController {
    
    private final WompiPaymentService wompiPaymentService;
    private final WompiService wompiService;
    
    /**
     * Obtener configuración pública de Wompi
     * El frontend necesita la public key para tokenizar tarjetas
     * 
     * GET /api/payments/wompi/config
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getPublicConfig() {
        log.info("📋 Obteniendo configuración pública de Wompi");
        
        Map<String, Object> config = new HashMap<>();
        config.put("publicKey", wompiService.getPublicKey());
        config.put("currency", "COP");
        config.put("minAmount", 5000);
        config.put("maxAmount", 10000000);
        config.put("paymentMethods", new String[]{"CARD", "NEQUI", "PSE"});
        
        return ResponseEntity.ok(config);
    }
    
    /**
     * Iniciar depósito con Wompi
     * 
     * POST /api/payments/wompi/deposit
     * 
     * Body ejemplo para TARJETA:
     * {
     *   "amount": 50000,
     *   "paymentMethod": "CARD",
     *   "email": "user@example.com",
     *   "fullName": "Juan Pérez",
     *   "phone": "+573001234567",
     *   "cardToken": "tok_test_12345_ABCDEF",
     *   "installments": 1
     * }
     * 
     * Body ejemplo para NEQUI:
     * {
     *   "amount": 50000,
     *   "paymentMethod": "NEQUI",
     *   "email": "user@example.com",
     *   "fullName": "Juan Pérez",
     *   "phone": "+573001234567",
     *   "nequiPhone": "+573001234567"
     * }
     * 
     * Body ejemplo para PSE:
     * {
     *   "amount": 50000,
     *   "paymentMethod": "PSE",
     *   "email": "user@example.com",
     *   "fullName": "Juan Pérez",
     *   "phone": "+573001234567",
     *   "userType": 0,
     *   "idType": "CC",
     *   "idNumber": "1234567890",
     *   "bankCode": "1007"
     * }
     */
    @PostMapping("/deposit")
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    public ResponseEntity<WompiDepositResponse> initiateDeposit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid WompiDepositRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = jwt.getClaim("userId");
        String ipAddress = getClientIp(httpRequest);
        
        log.info("💳 Usuario {} inicia depósito de ${} con {}", 
                userId, request.getAmount(), request.getPaymentMethod());
        
        WompiDepositResponse response = wompiPaymentService.initiateDeposit(userId, request, ipAddress);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Obtener lista de bancos PSE
     * El frontend usa esto para mostrar selector de banco
     * 
     * GET /api/payments/wompi/pse/banks
     * 
     * Respuesta ejemplo:
     * {
     *   "banks": [
     *     {
     *       "financial_institution_code": "1007",
     *       "financial_institution_name": "BANCOLOMBIA"
     *     },
     *     {
     *       "financial_institution_code": "1051",
     *       "financial_institution_name": "DAVIVIENDA"
     *     }
     *   ]
     * }
     */
    @GetMapping("/pse/banks")
    public ResponseEntity<Map<String, Object>> getPSEBanks() {
        log.info("🏦 Obteniendo lista de bancos PSE");
        
        try {
            JsonNode response = wompiService.getPSEBanks();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("banks", response.get("data"));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("❌ Error obteniendo bancos PSE: {}", e.getMessage());
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Error al obtener bancos PSE");
            
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Consultar estado de una transacción
     * Útil para verificar estado cuando el webhook falla
     * 
     * GET /api/payments/wompi/transaction/{wompiTransactionId}/status
     * 
     * Respuesta ejemplo:
     * {
     *   "success": true,
     *   "status": "APPROVED",
     *   "transaction": { ... }
     * }
     */
    @GetMapping("/transaction/{wompiTransactionId}/status")
    @PreAuthorize("hasRole('ROLE_CONSUMER')")
    public ResponseEntity<Map<String, Object>> getTransactionStatus(
            @PathVariable String wompiTransactionId
    ) {
        log.info("🔍 Consultando estado de transacción: {}", wompiTransactionId);
        
        try {
            JsonNode response = wompiService.getTransactionState(wompiTransactionId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("transaction", response.get("data"));
            result.put("status", response.get("data").get("status").asText());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("❌ Error consultando transacción: {}", e.getMessage());
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Error al consultar transacción");
            
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    // ========== MÉTODOS AUXILIARES ==========
    
    /**
     * Obtener IP real del cliente (considerando proxies)
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // Si hay múltiples IPs (proxy chain), tomar la primera
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
}
