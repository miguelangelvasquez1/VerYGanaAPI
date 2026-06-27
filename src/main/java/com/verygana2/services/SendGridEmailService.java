package com.verygana2.services;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

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

    private final SendGrid sendGrid;
    private final MoneyMapper moneyMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    @Async
    public void sendPurchaseConfirmation(Purchase purchase, String contactEmail) {

        log.info("Sending purchase confirmation email for purchase ID: {}", purchase.getId());

        try {

            String recipientEmail = getRecipientEmail(purchase, contactEmail);

            log.info("Recipient email determined: {}", recipientEmail);

            if (recipientEmail == null || recipientEmail.isBlank()) {
                log.error("No recipient email found for purchase ID: {}", purchase.getId());
                return;
            }

            String subject = String.format("✅ Confirmación de Compra - Orden #%d", purchase.getId());
            String htmlContent = buildPurchaseConfirmationHtml(purchase);

            log.info("Attempting to send email to: {}", recipientEmail);

            boolean sent = sendEmail(recipientEmail, subject, htmlContent);

            if (sent) {
                log.info("Purchase confirmation email sent successfully to: {}", recipientEmail);
            } else {
                log.error("Failed to send purchase confirmation email to: {}", recipientEmail);
            }

        } catch (Exception e) {
            log.error("Error sending purchase confirmation email for purchase ID: {}",
                    purchase.getId(), e);
        }
    }

    @Override
    //@Async
    public void sendCommercialSaleNotification(Purchase purchase) {
        log.info("Sending commercial sale notification for purchase ID: {}", purchase.getId());

        // Agrupar por vendedor y enviar una notificación a cada uno
        purchase.getItems().stream()
                .map(item -> item.getProduct().getCommercial())
                .distinct()
                .forEach(commercial -> {
                    try {
                        String commercialEmail = commercial.getUser().getEmail();
                        String subject = String.format("🎉 Nueva Venta - Orden #%d", purchase.getId());
                        String htmlContent = buildCommercialNotificationHtml(purchase, commercial.getId());

                        boolean sent = sendEmail(commercialEmail, subject, htmlContent);

                        if (sent) {
                            log.info("Commercial notification sent to: {}", commercialEmail);
                        }
                    } catch (Exception e) {
                        log.error("Error sending commercial notification to commercial ID: {}",
                                commercial.getId(), e);
                    }
                });
    }

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
                log.debug("Email sent successfully. Status: {}", statusCode);
                return true;
            } else {
                log.error("SendGrid returned error. Status: {}, Body: {}",
                        statusCode, response.getBody());
                return false;
            }

        } catch (IOException e) {
            log.error("IOException while sending email to: {}", toEmail, e);
            return false;
        }
    }

    /**
     * Obtiene el email del destinatario (contactEmail o email del usuario)
     */
    private String getRecipientEmail(Purchase purchase, String contactEmail) {
        if (contactEmail != null && !contactEmail.isBlank()) {
            return contactEmail;
        }
        return purchase.getConsumer().getUser().getEmail();
    }

    /**
     * Construye el HTML del email de confirmación de compra
     */
    private String buildPurchaseConfirmationHtml(Purchase purchase) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>");
        html.append("<html lang='es'>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<style>");
        html.append("* { margin: 0; padding: 0; box-sizing: border-box; }");
        html.append(
                "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; line-height: 1.6; color: #333; background-color: #f4f4f4; }");
        html.append(
                ".container { max-width: 600px; margin: 20px auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }");
        html.append(
                ".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px 20px; text-align: center; }");
        html.append(".header h1 { font-size: 28px; margin-bottom: 10px; }");
        html.append(".header p { font-size: 16px; opacity: 0.9; }");
        html.append(".content { padding: 30px; }");
        html.append(".order-info { background: #f9f9f9; padding: 20px; border-radius: 8px; margin-bottom: 20px; }");
        html.append(".order-info p { margin: 8px 0; font-size: 14px; }");
        html.append(".order-info strong { color: #667eea; }");
        html.append(".products-title { font-size: 20px; font-weight: bold; margin: 30px 0 20px; color: #333; }");
        html.append(
                ".product-item { background: white; border: 2px solid #e0e0e0; border-radius: 8px; padding: 20px; margin-bottom: 15px; }");
        html.append(".product-name { font-size: 18px; font-weight: bold; color: #667eea; margin-bottom: 10px; }");
        html.append(".product-price { font-size: 16px; color: #666; margin-bottom: 15px; }");
        html.append(
                ".code-section { background: linear-gradient(135deg, #e8f4f8 0%, #f0e8f8 100%); padding: 15px; border-radius: 6px; margin: 10px 0; }");
        html.append(
                ".code-label { font-size: 12px; font-weight: bold; color: #667eea; text-transform: uppercase; margin-bottom: 5px; }");
        html.append(
                ".code-value { font-family: 'Courier New', monospace; font-size: 16px; font-weight: bold; color: #333; word-break: break-all; }");
        html.append(
                ".credentials { background: #fff9e6; padding: 15px; border-radius: 6px; border-left: 4px solid #ffd700; margin: 10px 0; }");
        html.append(
                ".instructions { background: #e8f8f5; padding: 15px; border-radius: 6px; font-size: 14px; margin: 10px 0; }");
        html.append(
                ".footer { background: #f9f9f9; padding: 20px; text-align: center; border-top: 1px solid #e0e0e0; }");
        html.append(".footer p { font-size: 14px; color: #666; margin: 5px 0; }");
        html.append(".footer a { color: #667eea; text-decoration: none; }");
        html.append(".divider { height: 1px; background: #e0e0e0; margin: 20px 0; }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        html.append("<div class='container'>");

        // Header
        html.append("<div class='header'>");
        html.append("<h1>¡Gracias por tu compra! 🎉</h1>");
        html.append(String.format("<p>Orden #%d</p>", purchase.getId()));
        html.append("</div>");

        // Content
        html.append("<div class='content'>");

        // Order Info
        html.append("<div class='order-info'>");
        html.append(String.format("<p><strong>📅 Fecha:</strong> %s</p>",
                purchase.getCreatedAt().format(DATE_FORMATTER)));
        html.append(String.format("<p><strong>💰 Total Pagado:</strong> $%,.0f COP</p>",
                moneyMapper.fromCents(purchase.getTotalCents())));
        html.append("</div>");

        // Products
        html.append("<h2 class='products-title'>📦 Tus Productos Digitales</h2>");

        for (PurchaseItem item : purchase.getItems()) {
            html.append("<div class='product-item'>");
            html.append(String.format("<div class='product-name'>%s</div>",
                    escapeHtml(item.getProduct().getName())));
            html.append(String.format("<div class='product-price'>Precio: $%,.0f COP</div>",
                    moneyMapper.fromCents(item.getProduct().getPriceCents())));

            // Código
            if (item.getDeliveredCode() != null) {
                html.append("<div class='code-section'>");
                html.append("<div class='code-label'>🔑 Tu Código</div>");
                html.append(String.format("<div class='code-value'>%s</div>",
                        escapeHtml(item.getDeliveredCode())));
                html.append("</div>");
            }

            html.append("</div>");
        }

        html.append("<div class='divider'></div>");

        // Important Note
        html.append("<div style='background:#fff3cd;padding:15px;border-radius:6px;border-left:4px solid #ffc107;'>");
        html.append("<p style='margin:0;'><strong>⚠️ Importante:</strong> Guarda este email en un lugar seguro. ");
        html.append("Contiene información importante para acceder a tus productos digitales.</p>");
        html.append("</div>");

        html.append("</div>");

        // Footer
        html.append("<div class='footer'>");
        html.append("<p><strong>¿Necesitas ayuda?</strong></p>");
        html.append(String.format("<p>Contáctanos en <a href='mailto:%s'>%s</a></p>",
                supportEmail, supportEmail));
        html.append("<p style='margin-top:15px;font-size:12px;color:#999;'>");
        html.append("© 2025 VeryGana. Todos los derechos reservados.");
        html.append("</p>");
        html.append("</div>");

        html.append("</div>");
        html.append("</body>");
        html.append("</html>");

        return html.toString();
    }

    /**
     * Construye el HTML del email de notificación para el vendedor
     */
    private String buildCommercialNotificationHtml(Purchase purchase, Long commercialId) {
        // Similar structure pero enfocado en el vendedor
        // Por ahora retornaremos algo simple
        return String.format(
                "<h1>¡Nueva Venta!</h1><p>Tienes una nueva venta. Orden #%d</p>",
                purchase.getId());
    }

    /**
     * Escapa caracteres HTML para prevenir XSS
     */
    private String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    @Override
    @Async
    public void sendDesignerPasswordSetupEmail(String toEmail, String designerName, String setupLink, String designerCode) {
        log.info("Sending password setup email to designer: {}", toEmail);
        try {
            String subject = "Configura tu contraseña - VerYGana";
            String html = buildPasswordSetupHtml(designerName, setupLink, designerCode);
            boolean sent = sendEmail(toEmail, subject, html);
            if (!sent) {
                log.error("Failed to send password setup email to: {}", toEmail);
            }
        } catch (Exception e) {
            log.error("Error sending password setup email to: {}", toEmail, e);
        }
    }

    private String buildPasswordSetupHtml(String designerName, String setupLink, String designerCode) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<style>");
        html.append("* { margin:0; padding:0; box-sizing:border-box; }");
        html.append("body { font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif; line-height:1.6; color:#333; background:#f4f4f4; }");
        html.append(".container { max-width:600px; margin:20px auto; background:white; border-radius:10px; overflow:hidden; box-shadow:0 4px 6px rgba(0,0,0,0.1); }");
        html.append(".header { background:linear-gradient(135deg,#667eea 0%,#764ba2 100%); color:white; padding:40px 20px; text-align:center; }");
        html.append(".header h1 { font-size:26px; margin-bottom:8px; }");
        html.append(".header p { font-size:15px; opacity:0.9; }");
        html.append(".content { padding:32px; }");
        html.append(".greeting { font-size:18px; font-weight:bold; margin-bottom:16px; color:#333; }");
        html.append(".text { font-size:15px; color:#555; margin-bottom:16px; }");
        html.append(".btn-wrapper { text-align:center; margin:32px 0; }");
        html.append(".btn { display:inline-block; background:linear-gradient(135deg,#667eea 0%,#764ba2 100%); color:white; text-decoration:none; padding:14px 36px; border-radius:8px; font-size:16px; font-weight:bold; letter-spacing:0.5px; }");
        html.append(".fallback { background:#f9f9f9; border:1px solid #e0e0e0; border-radius:6px; padding:14px; margin-top:16px; font-size:13px; color:#666; word-break:break-all; }");
        html.append(".fallback strong { display:block; margin-bottom:6px; color:#333; }");
        html.append(".code-box { background:#f0f4ff; border:2px dashed #667eea; border-radius:8px; padding:16px; text-align:center; margin:24px 0; }");
        html.append(".code-box p { font-size:13px; color:#555; margin-bottom:8px; }");
        html.append(".code-box span { font-size:22px; font-weight:bold; letter-spacing:3px; color:#764ba2; font-family:monospace; }");
        html.append(".warning { background:#fff3cd; border-left:4px solid #ffc107; padding:14px; border-radius:4px; margin-top:24px; font-size:13px; }");
        html.append(".footer { background:#f9f9f9; padding:20px; text-align:center; border-top:1px solid #e0e0e0; font-size:13px; color:#666; }");
        html.append(".footer a { color:#667eea; text-decoration:none; }");
        html.append("</style></head><body><div class='container'>");

        html.append("<div class='header'>");
        html.append("<h1>Bienvenido/a a VerYGana</h1>");
        html.append("<p>Configura tu contraseña para empezar</p>");
        html.append("</div>");

        html.append("<div class='content'>");
        html.append("<p class='greeting'>Hola, ").append(escapeHtml(designerName)).append("</p>");
        html.append("<p class='text'>Tu cuenta de Game Designer ha sido creada en la plataforma VerYGana.</p>");
        html.append("<p class='text'>Para activar tu cuenta y comenzar a trabajar, necesitas crear una contraseña segura haciendo clic en el botón a continuación:</p>");

        html.append("<div class='code-box'>");
        html.append("<p>Tu código de diseñador (guárdalo, lo necesitarás para recuperar tu contraseña):</p>");
        html.append("<span>").append(escapeHtml(designerCode)).append("</span>");
        html.append("</div>");

        html.append("<div class='btn-wrapper'>");
        html.append("<a href='").append(setupLink).append("' class='btn'>Crear mi contraseña</a>");
        html.append("</div>");

        html.append("<div class='fallback'>");
        html.append("<strong>¿El botón no funciona? Copia y pega este enlace en tu navegador:</strong>");
        html.append(escapeHtml(setupLink));
        html.append("</div>");

        html.append("<div class='warning'>");
        html.append("⚠️ <strong>Importante:</strong> Este enlace es de un solo uso y expira en <strong>24 horas</strong>. ");
        html.append("Si no solicitaste esta cuenta, puedes ignorar este correo.");
        html.append("</div>");
        html.append("</div>");

        html.append("<div class='footer'>");
        html.append("<p><strong>¿Necesitas ayuda?</strong></p>");
        html.append(String.format("<p>Contáctanos en <a href='mailto:%s'>%s</a></p>", supportEmail, supportEmail));
        html.append("<p style='margin-top:12px;font-size:11px;color:#999;'>© 2025 VeryGana. Todos los derechos reservados.</p>");
        html.append("</div></div></body></html>");

        return html.toString();
    }

    @Override
    public boolean verifyEmail(String email, String code) {
        // Not used in the prize claim flow — email ownership is guaranteed by JWT auth
        return false;
    }

    @Override
    @Async
    public void sendPrizeClaimConfirmation(Prize prize, String consumerEmail, String decryptedClaimCode) {
        log.info("Sending prize claim confirmation to: {} for prize: {}", consumerEmail, prize.getId());
        try {
            String subject = "🎉 ¡Código de reclamación de tu premio! - " + prize.getTitle();
            String html = buildPrizeClaimHtml(prize, decryptedClaimCode);
            boolean sent = sendEmail(consumerEmail, subject, html);
            if (sent) {
                log.info("Prize claim email sent to: {}", consumerEmail);
            } else {
                log.error("Failed to send prize claim email to: {}", consumerEmail);
            }
        } catch (Exception e) {
            log.error("Error sending prize claim confirmation to: {}", consumerEmail, e);
        }
    }

    private String buildPrizeClaimHtml(Prize prize, String claimCode) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<style>");
        html.append("* { margin:0; padding:0; box-sizing:border-box; }");
        html.append("body { font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif; line-height:1.6; color:#333; background:#f4f4f4; }");
        html.append(".container { max-width:600px; margin:20px auto; background:white; border-radius:10px; overflow:hidden; box-shadow:0 4px 6px rgba(0,0,0,0.1); }");
        html.append(".header { background:linear-gradient(135deg,#f093fb 0%,#f5576c 100%); color:white; padding:40px 20px; text-align:center; }");
        html.append(".header h1 { font-size:28px; margin-bottom:10px; }");
        html.append(".content { padding:30px; }");
        html.append(".prize-box { background:#fff9e6; border:2px solid #ffc107; border-radius:8px; padding:20px; margin-bottom:24px; text-align:center; }");
        html.append(".prize-name { font-size:22px; font-weight:bold; color:#333; margin-bottom:6px; }");
        html.append(".prize-value { font-size:16px; color:#666; }");
        html.append(".code-box { background:linear-gradient(135deg,#667eea 0%,#764ba2 100%); border-radius:10px; padding:24px; text-align:center; margin:24px 0; }");
        html.append(".code-label { font-size:13px; font-weight:bold; color:rgba(255,255,255,0.8); text-transform:uppercase; letter-spacing:1px; margin-bottom:10px; }");
        html.append(".code-value { font-family:'Courier New',monospace; font-size:26px; font-weight:bold; color:white; letter-spacing:4px; word-break:break-all; }");
        html.append(".instructions { background:#e8f5e9; border-left:4px solid #4caf50; padding:16px; border-radius:4px; margin:16px 0; font-size:14px; }");
        html.append(".warning { background:#fff3cd; border-left:4px solid #ffc107; padding:14px; border-radius:4px; margin-top:16px; font-size:13px; }");
        html.append(".footer { background:#f9f9f9; padding:20px; text-align:center; border-top:1px solid #e0e0e0; font-size:13px; color:#666; }");
        html.append(".footer a { color:#667eea; text-decoration:none; }");
        html.append("</style></head><body><div class='container'>");

        html.append("<div class='header'><h1>🏆 ¡Felicitaciones, ganador/a!</h1>");
        html.append("<p>Tu código de reclamación está listo</p></div>");

        html.append("<div class='content'>");
        html.append("<div class='prize-box'>");
        html.append("<div class='prize-name'>").append(escapeHtml(prize.getTitle())).append("</div>");
        if (prize.getValue() != null) {
            html.append("<div class='prize-value'>Valor: $").append(String.format("%,.0f", prize.getValue())).append(" COP</div>");
        }
        html.append("</div>");

        html.append("<div class='code-box'>");
        html.append("<div class='code-label'>🔑 Tu código de reclamación</div>");
        html.append("<div class='code-value'>").append(escapeHtml(claimCode)).append("</div>");
        html.append("</div>");

        if (prize.getClaimInstructions() != null && !prize.getClaimInstructions().isBlank()) {
            html.append("<div class='instructions'>");
            html.append("<strong>📋 Instrucciones para reclamar tu premio:</strong><br/><br/>");
            html.append(escapeHtml(prize.getClaimInstructions()).replace("\n", "<br/>"));
            html.append("</div>");
        }

        html.append("<div class='warning'>⚠️ <strong>Importante:</strong> Guarda este correo en un lugar seguro. ");
        html.append("Este código es de uso único y personal. No lo compartas con nadie.</div>");
        html.append("</div>");

        html.append("<div class='footer'>");
        html.append("<p><strong>¿Necesitas ayuda?</strong></p>");
        html.append(String.format("<p>Contáctanos en <a href='mailto:%s'>%s</a></p>", supportEmail, supportEmail));
        html.append("<p style='margin-top:12px;font-size:11px;color:#999;'>© 2025 VeryGana. Todos los derechos reservados.</p>");
        html.append("</div></div></body></html>");

        return html.toString();
    }
}
