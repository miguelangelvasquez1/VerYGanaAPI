package com.verygana2.services.interfaces;

import com.verygana2.models.marketplace.Purchase;

public interface EmailService {
    void sendPurchaseConfirmation(Purchase purchase, String consumerEmail);
    void sendCommercialSaleNotification(Purchase purchase);
}
