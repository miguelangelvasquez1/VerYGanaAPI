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
import com.verygana2.models.products.Purchase;
import com.verygana2.models.products.PurchaseItem;
import com.verygana2.services.interfaces.EmailService;

@Service
public class SendGridEmailService implements EmailService{

    private static final Logger log = LoggerFactory.getLogger(SendGridEmailService.class);

    @Value("${sendgrid.api-key}")
    private String sendGridApiKey;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    @Value("${sendgrid.from-name}")
    private String fromName;

    @Value("${sendgrid.support-email}")
    private String supportEmail;

    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    
    @Override
    @Async
    public void sendPurchaseConfirmation(Purchase purchase) {
        log.info("Sending purchase confirmation email for purchase ID: {}", purchase.getId());
        
        try {
            String recipientEmail = getRecipientEmail(purchase);
            String subject = String.format("✅ Confirmación de Compra - Orden #%d", purchase.getId());
            String htmlContent = buildPurchaseConfirmationHtml(purchase);
            
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
    @Async
    public void sendSellerSaleNotification(Purchase purchase) {
        log.info("Sending seller sale notification for purchase ID: {}", purchase.getId());
        
        // Agrupar por vendedor y enviar una notificación a cada uno
        purchase.getItems().stream()
            .map(item -> item.getProduct().getSeller())
            .distinct()
            .forEach(seller -> {
                try {
                    String sellerEmail = seller.getUser().getEmail();
                    String subject = String.format("🎉 Nueva Venta - Orden #%d", purchase.getId());
                    String htmlContent = buildSellerNotificationHtml(purchase, seller.getId());
                    
                    boolean sent = sendEmail(sellerEmail, subject, htmlContent);
                    
                    if (sent) {
                        log.info("Seller notification sent to: {}", sellerEmail);
                    }
                } catch (Exception e) {
                    log.error("Error sending seller notification to seller ID: {}", 
                        seller.getId(), e);
                }
            });
    }

    private boolean sendEmail(String toEmail, String subject, String htmlContent) {
        try {
            Email from = new Email(fromEmail, fromName);
            Email to = new Email(toEmail);
            Content content = new Content("text/html", htmlContent);
            
            Mail mail = new Mail(from, subject, to, content);
            
            SendGrid sg = new SendGrid(sendGridApiKey);
            Request request = new Request();
            
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            
            Response response = sg.api(request);
            
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
    private String getRecipientEmail(Purchase purchase) {
        if (purchase.getContactEmail() != null && !purchase.getContactEmail().isBlank()) {
            return purchase.getContactEmail();
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
        html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; line-height: 1.6; color: #333; background-color: #f4f4f4; }");
        html.append(".container { max-width: 600px; margin: 20px auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }");
        html.append(".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px 20px; text-align: center; }");
        html.append(".header h1 { font-size: 28px; margin-bottom: 10px; }");
        html.append(".header p { font-size: 16px; opacity: 0.9; }");
        html.append(".content { padding: 30px; }");
        html.append(".order-info { background: #f9f9f9; padding: 20px; border-radius: 8px; margin-bottom: 20px; }");
        html.append(".order-info p { margin: 8px 0; font-size: 14px; }");
        html.append(".order-info strong { color: #667eea; }");
        html.append(".products-title { font-size: 20px; font-weight: bold; margin: 30px 0 20px; color: #333; }");
        html.append(".product-item { background: white; border: 2px solid #e0e0e0; border-radius: 8px; padding: 20px; margin-bottom: 15px; }");
        html.append(".product-name { font-size: 18px; font-weight: bold; color: #667eea; margin-bottom: 10px; }");
        html.append(".product-price { font-size: 16px; color: #666; margin-bottom: 15px; }");
        html.append(".code-section { background: linear-gradient(135deg, #e8f4f8 0%, #f0e8f8 100%); padding: 15px; border-radius: 6px; margin: 10px 0; }");
        html.append(".code-label { font-size: 12px; font-weight: bold; color: #667eea; text-transform: uppercase; margin-bottom: 5px; }");
        html.append(".code-value { font-family: 'Courier New', monospace; font-size: 16px; font-weight: bold; color: #333; word-break: break-all; }");
        html.append(".credentials { background: #fff9e6; padding: 15px; border-radius: 6px; border-left: 4px solid #ffd700; margin: 10px 0; }");
        html.append(".instructions { background: #e8f8f5; padding: 15px; border-radius: 6px; font-size: 14px; margin: 10px 0; }");
        html.append(".footer { background: #f9f9f9; padding: 20px; text-align: center; border-top: 1px solid #e0e0e0; }");
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
            purchase.getTotal()));
        html.append(String.format("<p><strong>📧 Email:</strong> %s</p>", 
            getRecipientEmail(purchase)));
        html.append("</div>");
        
        // Products
        html.append("<h2 class='products-title'>📦 Tus Productos Digitales</h2>");
        
        for (PurchaseItem item : purchase.getItems()) {
            html.append("<div class='product-item'>");
            html.append(String.format("<div class='product-name'>%s</div>", 
                escapeHtml(item.getProduct().getName())));
            html.append(String.format("<div class='product-price'>Precio: $%,.0f COP</div>", 
                item.getPriceAtPurchase()));
            
            // Código
            if (item.getDeliveredCode() != null) {
                html.append("<div class='code-section'>");
                html.append("<div class='code-label'>🔑 Tu Código</div>");
                html.append(String.format("<div class='code-value'>%s</div>", 
                    escapeHtml(item.getDeliveredCode())));
                html.append("</div>");
            }
            
            // Credenciales
            if (item.getDeliveredCredentials() != null) {
                html.append("<div class='credentials'>");
                html.append("<div class='code-label'>🔐 Credenciales de Acceso</div>");
                html.append(String.format("<pre style='margin:0;font-family:monospace;'>%s</pre>", 
                    escapeHtml(item.getDeliveredCredentials())));
                html.append("</div>");
            }
            
            // Instrucciones
            if (item.getDeliveryInstructions() != null) {
                html.append("<div class='instructions'>");
                html.append("<div class='code-label'>📋 Instrucciones de Uso</div>");
                html.append(String.format("<p>%s</p>", 
                    escapeHtml(item.getDeliveryInstructions()).replace("\n", "<br>")));
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
    private String buildSellerNotificationHtml(Purchase purchase, Long sellerId) {
        // Similar structure pero enfocado en el vendedor
        // Por ahora retornaremos algo simple
        return String.format(
            "<h1>¡Nueva Venta!</h1><p>Tienes una nueva venta. Orden #%d</p>", 
            purchase.getId()
        );
    }

    /**
     * Escapa caracteres HTML para prevenir XSS
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }
}

