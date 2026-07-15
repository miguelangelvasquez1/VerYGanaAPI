package com.verygana2.services.interfaces;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.pqrs.PqrsType;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.raffles.Prize;

public interface EmailService {

    // ===== COMERCIO =====
    void sendPurchaseConfirmation(Purchase purchase, String consumerEmail);
    void sendCommercialSaleNotification(Purchase purchase);
    void sendPrizeClaimConfirmation(Prize prize, String consumerEmail, String decryptedClaimCode);

    // ===== AUTH =====
    void sendVerificationCodeEmail(String toEmail, String code);
    void sendDesignerPasswordSetupEmail(String toEmail, String designerName, String setupLink, String designerCode);

    // ===== BRANDING FLOW =====
    void sendBrandingDesignerAssignedEmail(String toEmail, String designerName, String brandName, String gameName, String adminNotes);
    void sendBrandingDesignSubmittedEmail(String toEmail, String commercialName, String brandName, String gameName);
    void sendBrandingChangesRequestedEmail(String toEmail, String designerName, String brandName, String changeNotes);
    void sendBrandingReadyToLaunchEmail(String toEmail, String brandName, String gameName);
    void sendBrandingRejectedEmail(String toEmail, String commercialName, String brandName, String rejectionNotes);

    // ===== PQRS =====
    void sendPqrsReceivedConfirmation(String toEmail, String requesterName, String based, PqrsType type, ZonedDateTime dueDate);
    void sendPqrsAssignedToAdmin(String adminEmail, String adminName, String based, String subject, ZonedDateTime dueDate);
    void sendPqrsResolved(String toEmail, String requesterName, String based, String response);
    void sendPqrsSlaAlert(String adminEmail, String adminName, String based, ZonedDateTime dueDate);

    // ===== SEGURIDAD =====
    void sendSecurityAlertEmail(String adminEmail, String alertType, String severity, String source,
                                 String description, ZonedDateTime detectedAt);
}
