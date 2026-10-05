package com.verygana2.loadtest.stubs;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.verygana2.loadtest.LoadTestProperties;
import com.verygana2.models.enums.CommercialActivityType;
import com.verygana2.models.enums.pqrs.PqrsType;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.raffles.Prize;
import com.verygana2.services.interfaces.EmailService;

/**
 * SendGrid falso de la prueba de carga: no envía nada, espera la latencia configurada y
 * suma al contador. No escribe destinatarios, códigos ni enlaces en el log.
 *
 * <p>Mantiene {@code @Async} en los mismos métodos que {@code SendGridEmailService}: los
 * demás se ejecutan dentro de la transacción del llamador y retienen la conexión de BD
 * durante la latencia, como en producción.
 */
@Service
@Primary
@Profile("loadtest")
public class LoadTestEmailService implements EmailService {

    private final LoadTestProperties properties;
    private final StubCallCounter counter;

    public LoadTestEmailService(LoadTestProperties properties, StubCallCounter counter) {
        this.properties = properties;
        this.counter = counter;
    }

    private void simulate() {
        StubCallCounter.sleep(properties.getStubs().getEmailLatencyMs());
        counter.count(StubCallCounter.EMAIL);
    }

    @Async
    @Override
    public void sendPurchaseConfirmation(Purchase purchase, String consumerEmail) {
        simulate();
    }

    @Async
    @Override
    public void sendCommercialSaleNotification(Purchase purchase) {
        simulate();
    }

    @Async
    @Override
    public void sendPrizeClaimConfirmation(Prize prize, String consumerEmail, String decryptedClaimCode) {
        simulate();
    }

    @Async
    @Override
    public void sendPhysicalItemExpiredToConsumer(PurchaseItem item, String consumerEmail) {
        simulate();
    }

    @Async
    @Override
    public void sendPhysicalItemExpiredToCommercial(PurchaseItem item) {
        simulate();
    }

    @Override
    public void sendVerificationCodeEmail(String toEmail, String code) {
        simulate();
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String code) {
        simulate();
    }

    @Override
    public void sendDesignerPasswordSetupEmail(String toEmail, String designerName, String setupLink, String designerCode) {
        simulate();
    }

    @Override
    public void sendBrandingDesignerAssignedEmail(String toEmail, String designerName, String brandName, String gameName, String adminNotes) {
        simulate();
    }

    @Override
    public void sendBrandingDesignSubmittedEmail(String toEmail, String commercialName, String brandName, String gameName) {
        simulate();
    }

    @Override
    public void sendBrandingChangesRequestedEmail(String toEmail, String designerName, String brandName, String changeNotes) {
        simulate();
    }

    @Override
    public void sendBrandingReadyToLaunchEmail(String toEmail, String brandName, String gameName) {
        simulate();
    }

    @Override
    public void sendBrandingRejectedEmail(String toEmail, String commercialName, String brandName, String rejectionNotes) {
        simulate();
    }

    @Override
    public void sendCommercialContractApprovedEmail(String toEmail, String commercialName, int version, CommercialActivityType correctedActivityType) {
        simulate();
    }

    @Override
    public void sendCommercialContractRejectedEmail(String toEmail, String commercialName, String reason, boolean documentsIssue) {
        simulate();
    }

    @Override
    public void sendSubscriptionExpiredEmail(String toEmail, String commercialName) {
        simulate();
    }

    @Override
    public void sendRenewalReminderEmail(String toEmail, String commercialName, long daysRemaining) {
        simulate();
    }

    @Override
    public void sendPlanPaymentFailedEmail(String toEmail, String commercialName) {
        simulate();
    }

    @Override
    public void sendBudgetLowWarningEmail(String toEmail, String commercialName, boolean critical) {
        simulate();
    }

    @Override
    public void sendBudgetExhaustedEmail(String toEmail, String commercialName) {
        simulate();
    }

    @Override
    public void sendBudgetDormantEmail(String toEmail, String commercialName) {
        simulate();
    }

    @Override
    public void sendBudgetReplenishedEmail(String toEmail, String commercialName) {
        simulate();
    }

    @Override
    public void sendPqrsReceivedConfirmation(String toEmail, String requesterName, String based, PqrsType type, ZonedDateTime dueDate) {
        simulate();
    }

    @Override
    public void sendPqrsAssignedToAdmin(String adminEmail, String adminName, String based, String subject, ZonedDateTime dueDate) {
        simulate();
    }

    @Override
    public void sendPqrsResolved(String toEmail, String requesterName, String based, String response) {
        simulate();
    }

    @Override
    public void sendPqrsSlaAlert(String adminEmail, String adminName, String based, ZonedDateTime dueDate) {
        simulate();
    }

    @Override
    public void sendSecurityAlertEmail(String adminEmail, String alertType, String severity, String source, String description, ZonedDateTime detectedAt) {
        simulate();
    }
}
