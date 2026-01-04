package com.verygana2.services;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.dtos.wompi.WompiDepositRequest;
import com.verygana2.dtos.wompi.WompiDepositResponse;
import com.verygana2.models.Transaction;
import com.verygana2.models.Wallet;
import com.verygana2.models.enums.TransactionState;
import com.verygana2.repositories.TransactionRepository;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.services.interfaces.WompiPaymentService;
import com.verygana2.services.interfaces.WompiService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio orquestador de pagos con Wompi
 * Coordina entre WompiService, WalletService y TransactionRepository
 */

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class WompiPaymentServiceImpl implements WompiPaymentService {

    private final WompiService wompiService;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;


    @Override
    public WompiDepositResponse initiateDeposit(Long userId, WompiDepositRequest request, String ipAddress) {
        log.info("💳 Starting deposit for user: {}, amount: {}, method: {}", 
                userId, request.getAmount(), request.getPaymentMethod());
        
        // 1. Validar monto
        validateAmount(request.getAmount());
        
        // 2. Obtener wallet
        Wallet wallet = walletRepository.findByUserId(userId).orElseThrow(() -> new ObjectNotFoundException("Wallet from user id: " + userId + " not found", Wallet.class));
                
        
        // 3. Verificar que wallet esté activa
        if (!wallet.isActive()) {
            throw new IllegalStateException("Wallet is not active. Status: " + wallet.getStatus());
        }
        
        // 4. Convertir a centavos
        Long amountInCents = request.getAmount().multiply(new BigDecimal("100")).longValue();
        
        // 5. Crear transacción en BD (estado PENDING)
        Transaction transaction = Transaction.createWompiDepositTransaction(
                wallet,
                request.getAmount(),
                request.getPaymentMethod(),
                request.getEmail(),
                request.getFullName(),
                ipAddress
        );
        
        // 6. Guardar transacción
        transaction = transactionRepository.save(transaction);
        log.info("📝 Transaction created with ID: {} and reference: {}", 
                transaction.getId(), transaction.getReferenceId());
        
        try {
            // 7. Llamar a Wompi según método de pago
            JsonNode wompiResponse = callWompiByPaymentMethod(
                    request,
                    transaction.getReferenceId(),
                    amountInCents
            );
            
            // 8. Extraer datos de respuesta
            JsonNode data = wompiResponse.get("data");
            String wompiTxId = data.get("id").asText();
            String wompiStatus = data.get("status").asText();
            String paymentUrl = data.has("payment_link_url") ? data.get("payment_link_url").asText() : null;
            
            log.info("✅ Wompi response - ID: {}, Status: {}", wompiTxId, wompiStatus);
            
            // 9. Actualizar transacción con respuesta de Wompi
            transaction.updateWithWompiResponse(
                    wompiTxId,
                    wompiResponse.toString(),
                    wompiStatus
            );
            
            // 10. Guardar info del método de pago
            savePaymentMethodInfo(transaction, data);
            
            // 11. Actualizar wallet según estado
            updateWalletBasedOnStatus(wallet, transaction);
            
            // 12. Guardar cambios
            transactionRepository.save(transaction);
            walletRepository.save(wallet);
            
            // 13. Construir respuesta
            return WompiDepositResponse.builder()
                    .transactionId(transaction.getId())
                    .referenceId(transaction.getReferenceId())
                    .wompiTransactionId(wompiTxId)
                    .amount(request.getAmount())
                    .status(transaction.getTransactionState())
                    .paymentUrl(paymentUrl)
                    .message(getMessageForStatus(transaction.getTransactionState()))
                    .createdAt(transaction.getCreatedAt())
                    .build();
            
        } catch (Exception e) {
            log.error("❌ Processing payment error: {}", e.getMessage());
            
            // Marcar transacción como fallida
            transaction.markAsDeclined("Error: " + e.getMessage());
            transactionRepository.save(transaction);
            
            throw new RuntimeException("Processing payment error: " + e.getMessage(), e);
        }
    }

    @Override
    public void confirmDeposit(String wompiTransactionId) {
        log.info("✅ Confirming Wompi deposit transaction: {}", wompiTransactionId);
        
        Transaction transaction = transactionRepository.findByWompiTransactionId(wompiTransactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + wompiTransactionId));
        
        if (transaction.getTransactionState() == TransactionState.COMPLETED) {
            log.warn("⚠️ Transaction already was confirmed: {}", transaction.getId());
            return;
        }
        
        Wallet wallet = transaction.getWallet();
        
        // Si estaba en PENDING, mover de pendingBalance a balance
        if (transaction.getTransactionState() == TransactionState.PENDING || 
            transaction.getTransactionState() == TransactionState.PROCESSING) {
            
            wallet.confirmPendingDeposit(transaction.getAmount());
            
        } else {
            // Si era aprobación instantánea (tarjetas)
            wallet.addInstantDeposit(transaction.getAmount());
        }
        
        transaction.markAsApproved();
        
        transactionRepository.save(transaction);
        walletRepository.save(wallet);
        
        log.info("💰 Updated balance for user: {}. New balance: {}", 
                wallet.getUser().getId(), wallet.getBalance());
    }

    @Override
    public void declineDeposit(String wompiTransactionId, String reason) {
        log.info("❌ Rejecting Wompi deposit transaction: {}", wompiTransactionId);
        
        Transaction transaction = transactionRepository.findByWompiTransactionId(wompiTransactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + wompiTransactionId));
        
        if (transaction.getTransactionState() == TransactionState.FAILED) {
            log.warn("⚠️ Transaction already was marked as failed: {}", transaction.getId());
            return;
        }
        
        Wallet wallet = transaction.getWallet();
        
        // Si había balance pendiente, cancelarlo
        if (wallet.getPendingBalance().compareTo(BigDecimal.ZERO) > 0) {
            wallet.cancelPendingDeposit(transaction.getAmount());
            walletRepository.save(wallet);
        }
        
        transaction.markAsDeclined(reason);
        transactionRepository.save(transaction);
        
        log.info("⚠️ Deposit rejected for user: {}. Reason: {}", 
                wallet.getUser().getId(), reason);
    }

    // ========== MÉTODOS PRIVADOS ==========
    
    private void validateAmount(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("5000")) < 0) {
            throw new IllegalArgumentException("Minimum amount: $5.000 COP");
        }
        if (amount.compareTo(new BigDecimal("10000000")) > 0) {
            throw new IllegalArgumentException("Maximum amount: $10.000.000 COP");
        }
    }
    
    private JsonNode callWompiByPaymentMethod(
            WompiDepositRequest request,
            String reference,
            Long amountInCents
    ) {
        return switch (request.getPaymentMethod()) {
            case CARD -> wompiService.createCardTransaction(request, reference, amountInCents);
            case NEQUI -> wompiService.createNequiTransaction(request, reference, amountInCents);
            case PSE -> wompiService.createPSETransaction(request, reference, amountInCents);
            default -> throw new IllegalArgumentException("Payment method not allowed: " + request.getPaymentMethod());
        };
    }
    
    private void savePaymentMethodInfo(Transaction transaction, JsonNode data) {
        try {
            Map<String, String> info = new HashMap<>();
            info.put("type", transaction.getPaymentMethod().toString());
            
            if (data.has("payment_method")) {
                JsonNode paymentMethod = data.get("payment_method");
                
                if (paymentMethod.has("extra")) {
                    JsonNode extra = paymentMethod.get("extra");
                    if (extra.has("brand")) {
                        info.put("brand", extra.get("brand").asText());
                    }
                    if (extra.has("last_four")) {
                        info.put("lastFour", extra.get("last_four").asText());
                    }
                    if (extra.has("bank_name")) {
                        info.put("bankName", extra.get("bank_name").asText());
                    }
                }
            }
            
            transaction.setPaymentMethodInfoFromMap(info);
            
        } catch (Exception e) {
            log.warn("⚠️ it was not possible save the payment method info: {}", e.getMessage());
        }
    }
    
    private void updateWalletBasedOnStatus(Wallet wallet, Transaction transaction) {
        TransactionState status = transaction.getTransactionState();
        
        if (status == TransactionState.PENDING || status == TransactionState.PROCESSING) {
            // PSE, Nequi - agregar a pending
            wallet.addPendingBalance(transaction.getAmount());
            log.info("⏳ Pending balanced added: {}", transaction.getAmount());
            
        } else if (status == TransactionState.COMPLETED) {
            // Tarjeta aprobada instantáneamente
            wallet.addInstantDeposit(transaction.getAmount());
            log.info("✅ Instant deposit: {}", transaction.getAmount());
        }
        // Si es FAILED/CANCELLED, no hacer nada con el wallet
    }
    
    private String getMessageForStatus(TransactionState status) {
        return switch (status) {
            case COMPLETED -> "Payment approved succesfully";
            case PENDING -> "Pending payment";
            case PROCESSING -> "Payment is in process";
            case FAILED -> "Rejected payment";
            case CANCELLED -> "Canceled payment";
        };
    }
}
