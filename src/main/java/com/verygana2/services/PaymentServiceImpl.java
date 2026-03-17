package com.verygana2.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;


import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.verygana2.config.WompiConfig;
import com.verygana2.dtos.wompi.PaymentInitResponseDTO;
import com.verygana2.dtos.wompi.PaymentStatusResponseDTO;
import com.verygana2.dtos.wompi.WompiWebhookEventDTO;
import com.verygana2.models.Payment;
import com.verygana2.models.enums.wompi.PaymentStatus;
import com.verygana2.repositories.PaymentRepository;
import com.verygana2.services.interfaces.wompi.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService{

    private final WompiConfig wompiConfig;
    private final PaymentRepository paymentRepository;

    // ─────────────────────────────────────────────
    // 4.1 Generar referencia + firma de integridad
    // ─────────────────────────────────────────────
    @Override
    public PaymentInitResponseDTO initiatePayment(Long userId, String userEmail, Long amountInCents) {
        // Referencia única por transacción
        String reference = "TXN-" + userId + "-" + System.currentTimeMillis();

        // Calcular firma SHA-256
        // Formato Wompi: reference + amountInCents + currency + integritySecret
        String toHash = reference + amountInCents + "COP" + wompiConfig.getIntegrityKey();
        String integrityHash = sha256(toHash);

        // Persistir pago en estado PENDING
        Payment payment = Payment.builder()
                .reference(reference)
                .amountInCents(amountInCents)
                .currency("COP")
                .status(PaymentStatus.PENDING)
                .userId(userId)
                .userEmail(userEmail)
                .build();
        paymentRepository.save(payment);

        return PaymentInitResponseDTO.builder()
                .reference(reference)
                .amountInCents(amountInCents)
                .currency("COP")
                .publicKey(wompiConfig.getPublicKey())
                .integrityHash(integrityHash)
                .checkoutBaseUrl(wompiConfig.getCheckoutBaseUrl())
                .build();
    }

    // ─────────────────────────────────────────────
    // 4.2 Procesar webhook de Wompi
    // ─────────────────────────────────────────────
    @Override
    public void processWebhook(WompiWebhookEventDTO event, String wompiSignature) {
        // Validar que el webhook viene de Wompi
        validateWebhookSignature(event, wompiSignature);

        String wompiTransactionId = event.getData().getTransaction().getId();
        String reference = event.getData().getTransaction().getReference();
        String wompiStatus = event.getData().getTransaction().getStatus();

        Payment payment = paymentRepository.findByReference(reference)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + reference));

        payment.setWompiTransactionId(wompiTransactionId);
        payment.setStatus(mapWompiStatus(wompiStatus));
        paymentRepository.save(payment);

        log.info("Payment {} updated to status {}", reference, wompiStatus);
    }

    // ─────────────────────────────────────────────
    // 4.3 Consultar estado (llamado desde el frontend en la redirect_url)
    // ─────────────────────────────────────────────
    @Override
    public PaymentStatusResponseDTO getPaymentStatus(String reference, Long userId) {
        Payment payment = paymentRepository.findByReference(reference)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        // Seguridad: solo el dueño puede consultar su pago
        if (!payment.getUserId().equals(userId)) {
            throw new AccessDeniedException("Forbidden");
        }

        return PaymentStatusResponseDTO.builder()
                .reference(payment.getReference())
                .status(payment.getStatus())
                .amountInCents(payment.getAmountInCents())
                .wompiTransactionId(payment.getWompiTransactionId())
                .build();
    }

    // ─────────────────────────────────────────────
    // 4.4 Historial de pagos del usuario
    // ─────────────────────────────────────────────
    @Override
    public List<PaymentStatusResponseDTO> getUserPayments(Long userId) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(p -> PaymentStatusResponseDTO.builder()
                        .reference(p.getReference())
                        .status(p.getStatus())
                        .amountInCents(p.getAmountInCents())
                        .wompiTransactionId(p.getWompiTransactionId())
                        .createdAt(p.getCreatedAt())
                        .build())
                .toList();
    }



    // ─────────────────────────────────────────────
    // Utils privados
    // ─────────────────────────────────────────────
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void validateWebhookSignature(WompiWebhookEventDTO event, String wompiSignature) {
        // Wompi envía: X-Event-Checksum = SHA256(properties + timestamp + eventsKey)
        String timestamp = event.getTimestamp().toString();
        String toHash = event.getData().getTransaction().getId()
                + event.getData().getTransaction().getStatus()
                + event.getData().getTransaction().getAmountInCents()
                + timestamp
                + wompiConfig.getEventsKey();

        String expectedHash = sha256(toHash);
        if (!expectedHash.equals(wompiSignature)) {
            throw new SecurityException("Invalid webhook signature");
        }
    }

    private PaymentStatus mapWompiStatus(String wompiStatus) {
        return switch (wompiStatus) {
            case "APPROVED" -> PaymentStatus.APPROVED;
            case "DECLINED" -> PaymentStatus.DECLINED;
            case "VOIDED"   -> PaymentStatus.VOIDED;
            case "ERROR"    -> PaymentStatus.ERROR;
            default         -> PaymentStatus.PENDING;
        };
    }
}