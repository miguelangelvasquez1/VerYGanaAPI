package com.verygana2.services.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import com.verygana2.dtos.wompi.WompiDepositRequest;

public interface WompiService {
    
    JsonNode createCardTransaction (WompiDepositRequest request, String reference, Long amountInCents);
    JsonNode createNequiTransaction (WompiDepositRequest request, String reference, Long amountInCents);
    JsonNode createPSETransaction (WompiDepositRequest request, String reference, Long amountInCents);
    JsonNode getTransactionState (String transactionId);
    JsonNode getPSEBanks ();
    String getPublicKey ();
    boolean validateWebhookSignature(String payload, String signatue, String timestamp, String sentAt);
}
