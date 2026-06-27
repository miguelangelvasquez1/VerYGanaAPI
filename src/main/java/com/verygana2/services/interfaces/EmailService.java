package com.verygana2.services.interfaces;

import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.raffles.Prize;

public interface EmailService {
    void sendPurchaseConfirmation(Purchase purchase, String consumerEmail);
    void sendCommercialSaleNotification(Purchase purchase);
    void sendPrizeClaimConfirmation(Prize prize, String consumerEmail, String decryptedClaimCode);
    void sendDesignerPasswordSetupEmail(String toEmail, String designerName, String setupLink, String designerCode);
    boolean verifyEmail(String email, String code);
}
