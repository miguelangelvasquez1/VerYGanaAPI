package com.verygana2.services.interfaces;

import java.io.IOException;

import com.verygana2.models.products.Purchase;

public interface EmailService {
    void sendPurchaseConfirmation(Purchase purchase);
    void sendSellerSaleNotification(Purchase purchase);
}
