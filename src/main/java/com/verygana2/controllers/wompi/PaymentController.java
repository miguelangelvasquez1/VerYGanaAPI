package com.verygana2.controllers.wompi;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.wompi.PaymentInitResponseDTO;
import com.verygana2.dtos.wompi.PaymentStatusResponseDTO;
import com.verygana2.dtos.wompi.WompiWebhookEventDTO;
import com.verygana2.services.interfaces.UserService;
import com.verygana2.services.interfaces.wompi.PaymentService;

import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final UserService userService;

    // POST /api/payments/initiate
    // 🔒 Requiere JWT — el frontend llama esto antes de redirigir a Wompi
    @PostMapping("/initiate")
    public ResponseEntity<PaymentInitResponseDTO> initiatePayment(
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");

        String email = userService.getUserById(userId).getEmail();
        Long fixedAmount = 5000000L; // 50.000 COP en centavos — tu monto fijo

        PaymentInitResponseDTO response = paymentService.initiatePayment(userId, email, fixedAmount);
        return ResponseEntity.ok(response);
    }

    // GET /api/payments/status?reference=TXN-123-456
    // 🔒 Requiere JWT — el frontend llama esto al volver de Wompi
    @GetMapping("/status")
    public ResponseEntity<PaymentStatusResponseDTO> getStatus(
            @RequestParam String reference,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(paymentService.getPaymentStatus(reference, userId));
    }

    // GET /api/payments/history
    // 🔒 Requiere JWT — historial del usuario autenticado
    @GetMapping("/history")
    public ResponseEntity<List<PaymentStatusResponseDTO>> getHistory(
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = jwt.getClaim("userId");
        return ResponseEntity.ok(paymentService.getUserPayments(userId));
    }

    // POST /api/payments/webhook
    // ⚠️ SIN JWT — Wompi llama aquí directamente, solo validamos la firma interna
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody WompiWebhookEventDTO event,
            @RequestHeader("X-Event-Checksum") String wompiSignature) {

        paymentService.processWebhook(event, wompiSignature);
        return ResponseEntity.ok().build();
    }

}
