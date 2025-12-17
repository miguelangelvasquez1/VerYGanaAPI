package com.verygana2.services.interfaces;

import com.verygana2.models.products.Purchase;

public interface EmailService {
    void sendPurchaseConfirmation(Purchase purchase, String consumerEmail);
    void sendSellerSaleNotification(Purchase purchase);
}
