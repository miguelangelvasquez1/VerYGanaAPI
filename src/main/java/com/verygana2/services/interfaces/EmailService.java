package com.verygana2.services.interfaces;

import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.raffles.Prize;

public interface EmailService {

    // ===== COMERCIO =====
    void sendPurchaseConfirmation(Purchase purchase, String consumerEmail);
    void sendCommercialSaleNotification(Purchase purchase);
    void sendPrizeClaimConfirmation(Prize prize, String consumerEmail, String decryptedClaimCode);

    // ===== AUTH =====
    boolean verifyEmail(String email, String code);
    void sendAccountVerificationEmail(String toEmail, String verificationUrl);
    void sendDesignerPasswordSetupEmail(String toEmail, String designerName, String setupLink, String designerCode);

    // ===== BRANDING FLOW =====
    void sendBrandingDesignerAssignedEmail(String toEmail, String designerName, String brandName, String gameName, String adminNotes);
    void sendBrandingDesignSubmittedEmail(String toEmail, String commercialName, String brandName, String gameName);
    void sendBrandingChangesRequestedEmail(String toEmail, String designerName, String brandName, String changeNotes);
    void sendBrandingReadyToLaunchEmail(String toEmail, String brandName, String gameName);
    void sendBrandingRejectedEmail(String toEmail, String commercialName, String brandName, String rejectionNotes);
}
