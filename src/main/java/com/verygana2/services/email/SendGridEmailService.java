package com.verygana2.services.email;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.verygana2.mappers.finance.MoneyMapper;
import com.verygana2.models.enums.pqrs.PqrsType;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.raffles.Prize;
import com.verygana2.services.interfaces.EmailService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SendGridEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SendGridEmailService.class);

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    @Value("${sendgrid.from-name}")
    private String fromName;

    @Value("${sendgrid.support-email}")
    private String supportEmail;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    private final SendGrid sendGrid;
    private final MoneyMapper moneyMapper;
    private final EmailTemplateLoader templateLoader;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ===== COMERCIO =====

    @Override
    @Async
    public void sendPurchaseConfirmation(Purchase purchase, String contactEmail) {
        log.info("Sending purchase confirmation email for purchase ID: {}", purchase.getId());
        try {
            String recipientEmail = (contactEmail != null && !contactEmail.isBlank())
                    ? contactEmail
                    : purchase.getConsumer().getUser().getEmail();

            if (recipientEmail == null || recipientEmail.isBlank()) {
                log.error("No recipient email found for purchase ID: {}", purchase.getId());
                return;
            }

            String html = templateLoader.render("purchase-confirmation.html", Map.of(
                    "orderId", String.valueOf(purchase.getId()),
                    "date", purchase.getCreatedAt().format(DATE_FORMATTER),
                    "total", String.format("%,.0f", moneyMapper.fromCents(purchase.getTotalCents())),
                    "itemsHtml", buildItemsHtml(purchase),
                    "supportEmail", supportEmail));

            sendEmail(recipientEmail, "✅ Confirmación de Compra - Orden #" + purchase.getId(), html);
        } catch (Exception e) {
            log.error("Error sending purchase confirmation email for purchase ID: {}", purchase.getId(), e);
        }
    }

    @Override
    public void sendCommercialSaleNotification(Purchase purchase) {
        log.info("Sending commercial sale notification for purchase ID: {}", purchase.getId());
        purchase.getItems().stream()
                .map(item -> item.getProduct().getCommercial())
                .distinct()
                .forEach(commercial -> {
                    try {
                        String html = templateLoader.render("purchase-commercial-notification.html", Map.of(
                                "orderId", String.valueOf(purchase.getId()),
                                "supportEmail", supportEmail));
                        sendEmail(commercial.getUser().getEmail(), "🎉 Nueva Venta - Orden #" + purchase.getId(), html);
                    } catch (Exception e) {
                        log.error("Error sending commercial notification to commercial ID: {}", commercial.getId(), e);
                    }
                });
    }

    @Override
    @Async
    public void sendPrizeClaimConfirmation(Prize prize, String consumerEmail, String decryptedClaimCode) {
        log.info("Sending prize claim confirmation to: {} for prize: {}", consumerEmail, prize.getId());
        try {
            String prizeImageSection = (prize.getImageUrl() != null)
                    ? "<img class='prize-image' src='" + prize.getImageUrl() + "' alt='" + escapeHtml(prize.getTitle()) + "'>"
                    : "";
            String prizeValueSection = (prize.getValue() != null)
                    ? "<div class='prize-value'>Valor: $" + String.format("%,.0f", prize.getValue()) + " COP</div>"
                    : "";
            String claimInstructionsSection = (prize.getClaimInstructions() != null && !prize.getClaimInstructions().isBlank())
                    ? "<div class='instructions'><strong>Instrucciones para reclamar tu premio:</strong><br/><br/>"
                            + escapeHtml(prize.getClaimInstructions()).replace("\n", "<br/>") + "</div>"
                    : "";

            String html = templateLoader.render("prize-claim.html", Map.of(
                    "prizeTitle", escapeHtml(prize.getTitle()),
                    "prizeImageSection", prizeImageSection,
                    "prizeValueSection", prizeValueSection,
                    "claimCode", escapeHtml(decryptedClaimCode),
                    "claimInstructionsSection", claimInstructionsSection,
                    "supportEmail", supportEmail));

            sendEmail(consumerEmail, "🎉 ¡Código de reclamación de tu premio! - " + prize.getTitle(), html);
        } catch (Exception e) {
            log.error("Error sending prize claim confirmation to: {}", consumerEmail, e);
        }
    }

    // ===== AUTH =====

    @Override
    @Async
    public void sendVerificationCodeEmail(String toEmail, String code) {
        log.info("Sending verification code email to: {}", toEmail);
        try {
            String html = templateLoader.render("verification-code.html", Map.of(
                    "verificationCode", code,
                    "supportEmail", supportEmail));
            sendEmail(toEmail, "✉️ Tu código de verificación - VerYGana", html);
        } catch (Exception e) {
            log.error("Error sending verification code email to: {}", toEmail, e);
        }
    }

    @Override
    @Async
    public void sendPasswordResetEmail(String toEmail, String code) {
        log.info("Sending password reset email to: {}", toEmail);
        try {
            String html = templateLoader.render("password-reset.html", Map.of(
                    "resetCode", code,
                    "supportEmail", supportEmail));
            sendEmail(toEmail, "🔑 Recupera tu cuenta - VerYGana", html);
        } catch (Exception e) {
            log.error("Error sending password reset email to: {}", toEmail, e);
        }
    }

    @Override
    @Async
    public void sendDesignerPasswordSetupEmail(String toEmail, String designerName, String setupLink, String designerCode) {
        log.info("Sending password setup email to designer: {}", toEmail);
        try {
            String html = templateLoader.render("designer-password-setup.html", Map.of(
                    "designerName", escapeHtml(designerName),
                    "designerCode", escapeHtml(designerCode),
                    "setupLink", setupLink,
                    "supportEmail", supportEmail));
            sendEmail(toEmail, "Configura tu contraseña - VerYGana", html);
        } catch (Exception e) {
            log.error("Error sending password setup email to: {}", toEmail, e);
        }
    }

    // ===== BRANDING FLOW =====

    @Override
    @Async
    public void sendBrandingDesignerAssignedEmail(String toEmail, String designerName, String brandName,
            String gameName, String adminNotes) {
        log.info("Sending designer assigned email to: {}", toEmail);
        try {
            String adminNotesSection = (adminNotes != null && !adminNotes.isBlank())
                    ? "<div class='notes-box'><strong>Notas del equipo VerYGana:</strong>"
                            + escapeHtml(adminNotes) + "</div>"
                    : "";
            String html = templateLoader.render("branding-designer-assigned.html", Map.of(
                    "designerName", escapeHtml(designerName),
                    "brandName", escapeHtml(brandName),
                    "gameName", escapeHtml(gameName),
                    "adminNotesSection", adminNotesSection,
                    "platformUrl", frontendUrl,
                    "supportEmail", supportEmail));
            sendEmail(toEmail, "Nuevo proyecto asignado: " + brandName + " — VerYGana", html);
        } catch (Exception e) {
            log.error("Error sending designer assigned email to: {}", toEmail, e);
        }
    }

    @Override
    @Async
    public void sendBrandingDesignSubmittedEmail(String toEmail, String commercialName, String brandName,
            String gameName) {
        log.info("Sending design submitted email to: {}", toEmail);
        try {
            String html = templateLoader.render("branding-design-submitted.html", Map.of(
                    "commercialName", escapeHtml(commercialName),
                    "brandName", escapeHtml(brandName),
                    "gameName", escapeHtml(gameName),
                    "platformUrl", frontendUrl,
                    "supportEmail", supportEmail));
            sendEmail(toEmail, "Tu juego está listo para revisar — " + brandName, html);
        } catch (Exception e) {
            log.error("Error sending design submitted email to: {}", toEmail, e);
        }
    }

    @Override
    @Async
    public void sendBrandingChangesRequestedEmail(String toEmail, String designerName, String brandName,
            String changeNotes) {
        log.info("Sending changes requested email to: {}", toEmail);
        try {
            String html = templateLoader.render("branding-changes-requested.html", Map.of(
                    "designerName", escapeHtml(designerName),
                    "brandName", escapeHtml(brandName),
                    "changeNotes", escapeHtml(changeNotes != null ? changeNotes : ""),
                    "platformUrl", frontendUrl,
                    "supportEmail", supportEmail));
            sendEmail(toEmail, "Cambios solicitados en el diseño: " + brandName, html);
        } catch (Exception e) {
            log.error("Error sending changes requested email to: {}", toEmail, e);
        }
    }

    @Override
    @Async
    public void sendBrandingReadyToLaunchEmail(String toEmail, String brandName, String gameName) {
        log.info("Sending ready to launch email to: {}", toEmail);
        try {
            String html = templateLoader.render("branding-ready-to-launch.html", Map.of(
                    "brandName", escapeHtml(brandName),
                    "gameName", escapeHtml(gameName),
                    "platformUrl", frontendUrl,
                    "supportEmail", supportEmail));
            sendEmail(toEmail, "Campaña lista para lanzar: " + brandName, html);
        } catch (Exception e) {
            log.error("Error sending ready to launch email to: {}", toEmail, e);
        }
    }

    @Override
    @Async
    public void sendBrandingRejectedEmail(String toEmail, String commercialName, String brandName,
            String rejectionNotes) {
        log.info("Sending branding rejected email to: {}", toEmail);
        try {
            String html = templateLoader.render("branding-rejected.html", Map.of(
                    "commercialName", escapeHtml(commercialName),
                    "brandName", escapeHtml(brandName),
                    "rejectionNotes", escapeHtml(rejectionNotes != null ? rejectionNotes : ""),
                    "supportEmail", supportEmail));
            sendEmail(toEmail, "Solicitud de branding no aprobada — " + brandName, html);
        } catch (Exception e) {
            log.error("Error sending branding rejected email to: {}", toEmail, e);
        }
    }

    // ===== PQRS =====

    @Override
    @Async
    public void sendPqrsReceivedConfirmation(String toEmail, String requesterName, String based, PqrsType type,
            ZonedDateTime dueDate) {
        log.info("Sending PQRS received confirmation to: {} for radicado: {}", toEmail, based);
        try {
            String html = templateLoader.render("pqrs-received-confirmation.html", Map.of(
                    "requesterName", escapeHtml(requesterName),
                    "based", based,
                    "typeLabel", pqrsTypeLabel(type),
                    "dueDate", dueDate.format(DATE_FORMATTER),
                    "supportEmail", supportEmail));
            sendEmail(toEmail, "Recibimos tu " + pqrsTypeLabel(type).toLowerCase() + " — Radicado " + based, html);
        } catch (Exception e) {
            log.error("Error sending PQRS received confirmation to: {}", toEmail, e);
        }
    }

    @Override
    @Async
    public void sendPqrsAssignedToAdmin(String adminEmail, String adminName, String based, String subject,
            ZonedDateTime dueDate) {
        log.info("Sending PQRS assigned notification to admin: {} for radicado: {}", adminEmail, based);
        try {
            String html = templateLoader.render("pqrs-assigned-admin.html", Map.of(
                    "adminName", escapeHtml(adminName),
                    "based", based,
                    "subject", escapeHtml(subject),
                    "dueDate", dueDate.format(DATE_FORMATTER),
                    "platformUrl", frontendUrl,
                    "supportEmail", supportEmail));
            sendEmail(adminEmail, "Nuevo PQRS asignado — Radicado " + based, html);
        } catch (Exception e) {
            log.error("Error sending PQRS assigned notification to: {}", adminEmail, e);
        }
    }

    @Override
    @Async
    public void sendPqrsResolved(String toEmail, String requesterName, String based, String response) {
        log.info("Sending PQRS resolved notification to: {} for radicado: {}", toEmail, based);
        try {
            String html = templateLoader.render("pqrs-resolved.html", Map.of(
                    "requesterName", escapeHtml(requesterName),
                    "based", based,
                    "response", escapeHtml(response).replace("\n", "<br/>"),
                    "supportEmail", supportEmail));
            sendEmail(toEmail, "Tu PQRS fue resuelto — Radicado " + based, html);
        } catch (Exception e) {
            log.error("Error sending PQRS resolved notification to: {}", toEmail, e);
        }
    }

    @Override
    @Async
    public void sendPqrsSlaAlert(String adminEmail, String adminName, String based, ZonedDateTime dueDate) {
        log.info("Sending PQRS SLA alert to admin: {} for radicado: {}", adminEmail, based);
        try {
            String html = templateLoader.render("pqrs-sla-alert.html", Map.of(
                    "adminName", escapeHtml(adminName),
                    "based", based,
                    "dueDate", dueDate.format(DATE_FORMATTER),
                    "platformUrl", frontendUrl,
                    "supportEmail", supportEmail));
            sendEmail(adminEmail, "⚠️ PQRS próximo a vencer — Radicado " + based, html);
        } catch (Exception e) {
            log.error("Error sending PQRS SLA alert to: {}", adminEmail, e);
        }
    }

    // ===== SEGURIDAD =====

    @Override
    @Async
    public void sendSecurityAlertEmail(String adminEmail, String alertType, String severity, String source,
                                        String description, ZonedDateTime detectedAt) {
        log.info("Sending security alert email to: {} - type: {}", adminEmail, alertType);
        try {
            String html = templateLoader.render("security-alert.html", Map.of(
                    "alertType", escapeHtml(alertType),
                    "severity", escapeHtml(severity),
                    "source", escapeHtml(source),
                    "description", escapeHtml(description),
                    "detectedAt", detectedAt.format(DATE_FORMATTER),
                    "platformUrl", frontendUrl,
                    "supportEmail", supportEmail));
            sendEmail(adminEmail, "🚨 Alerta de seguridad crítica — " + alertType, html);
        } catch (Exception e) {
            log.error("Error sending security alert email to: {}", adminEmail, e);
        }
    }

    private String pqrsTypeLabel(PqrsType type) {
        return switch (type) {
            case PETICION -> "Petición";
            case QUEJA -> "Queja";
            case RECLAMO -> "Reclamo";
            case SUGERENCIA -> "Sugerencia";
        };
    }

    // ===== HELPERS =====

    private boolean sendEmail(String toEmail, String subject, String htmlContent) {
        try {
            Email from = new Email(fromEmail, fromName);
            Email to = new Email(toEmail);
            Content content = new Content("text/html", htmlContent);
            Mail mail = new Mail(from, subject, to, content);

            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sendGrid.api(request);
            int statusCode = response.getStatusCode();

            if (statusCode >= 200 && statusCode < 300) {
                log.debug("Email sent successfully to {}. Status: {}", toEmail, statusCode);
                return true;
            } else {
                log.error("SendGrid returned error. Status: {}, Body: {}", statusCode, response.getBody());
                return false;
            }
        } catch (IOException e) {
            log.error("IOException while sending email to: {}", toEmail, e);
            return false;
        }
    }

    private String buildItemsHtml(Purchase purchase) {
        StringBuilder sb = new StringBuilder();
        for (PurchaseItem item : purchase.getItems()) {
            String imageUrl = item.getProduct().getImageUrl();
            sb.append("<div class='product-item'>");
            sb.append("<div class='product-name'>").append(escapeHtml(item.getProduct().getName())).append("</div>");
            sb.append("<div class='product-price'>Precio: $")
                    .append(String.format("%,.0f", moneyMapper.fromCents(item.getProduct().getPriceCents())))
                    .append(" COP</div>");
            if (item.getDeliveredCode() != null) {
                sb.append("<div class='code-row'>");
                if (imageUrl != null) {
                    sb.append("<img class='product-thumb' src='").append(imageUrl).append("' alt=''>");
                }
                sb.append("<div class='code-section'>");
                sb.append("<div class='code-label'>Tu Código</div>");
                sb.append("<div class='code-value'>").append(escapeHtml(item.getDeliveredCode())).append("</div>");
                sb.append("</div>");
                sb.append("</div>");
            }
            sb.append("</div>");
        }
        return sb.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
