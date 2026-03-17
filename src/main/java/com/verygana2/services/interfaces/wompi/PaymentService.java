package com.verygana2.services.interfaces.wompi;

import java.util.List;

import com.verygana2.dtos.wompi.PaymentInitResponseDTO;
import com.verygana2.dtos.wompi.PaymentStatusResponseDTO;
import com.verygana2.dtos.wompi.WompiWebhookEventDTO;

public interface PaymentService {
    PaymentInitResponseDTO initiatePayment(Long userId, String userEmail, Long amountInCents);
    void processWebhook(WompiWebhookEventDTO event, String wompiSignature);
    PaymentStatusResponseDTO getPaymentStatus(String reference, Long userId);
    List<PaymentStatusResponseDTO> getUserPayments(Long userId);
}
