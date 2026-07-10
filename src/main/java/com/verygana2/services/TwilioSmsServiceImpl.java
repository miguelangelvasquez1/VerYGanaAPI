package com.verygana2.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.twilio.exception.ApiException;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import com.twilio.type.PhoneNumber;
import com.verygana2.models.raffles.Prize;
import com.verygana2.services.interfaces.TwilioSmsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioSmsServiceImpl implements TwilioSmsService {

    @Value("${twilio.verify-service-sid}")
    private String verifyServiceSid;

    @Value("${twilio.phone-number}")
    private String fromPhoneNumber;

    private final TwilioRestClient twilioRestClient;

    @Override
    public void sendOtp(String phoneNumber) {
        String e164 = toE164(phoneNumber);
        log.info("Enviando OTP a {}", e164);
        Verification.creator(verifyServiceSid, e164, "sms")
                .create(twilioRestClient);
    }

    @Override
    public boolean verifyOtp(String phoneNumber, String code) {
        String e164 = toE164(phoneNumber);
        try {
            VerificationCheck check = VerificationCheck.creator(verifyServiceSid)
                    .setTo(e164)
                    .setCode(code)
                    .create(twilioRestClient);
            return "approved".equals(check.getStatus());
        } catch (ApiException e) {
            // Twilio lanza ApiException cuando el código ya expiró o se agotaron los intentos
            log.warn("Twilio Verify falló para {}: {}", e164, e.getMessage());
            return false;
        }
    }

    /**
     * Convierte un número colombiano de 10 dígitos al formato E.164 (+57XXXXXXXXXX).
     * Si ya contiene el prefijo de país se normaliza igualmente.
     */
    private String toE164(String phoneNumber) {
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            return "+57" + digits;
        }
        if (digits.length() == 12 && digits.startsWith("57")) {
            return "+" + digits;
        }
        return "+" + digits;
    }

    @Override
    public void sendPrizeClaimConfirmation(Prize prize, String phoneNumber, String decryptedClaimCode) {
        String e164 = toE164(phoneNumber);
        log.info("Sending prize claim SMS to {} for prize {}", e164, prize.getId());
        try {
            String body = buildPrizeClaimSms(prize, decryptedClaimCode);
            Message.creator(
                    new PhoneNumber(e164),
                    new PhoneNumber(fromPhoneNumber),
                    body
            ).create(twilioRestClient);
            log.info("Prize claim SMS sent to {}", e164);
        } catch (Exception e) {
            log.error("Failed to send prize claim SMS to {}: {}", e164, e.getMessage());
            throw e;
        }
    }

    private String buildPrizeClaimSms(Prize prize, String claimCode) {
        StringBuilder sb = new StringBuilder();
        sb.append("Haz reclamado tu premio en VeryGana\n");
        sb.append("Premio: ").append(prize.getTitle()).append("\n");
        sb.append("Código de reclamación: ").append(claimCode).append("\n");
        if (prize.getClaimInstructions() != null && !prize.getClaimInstructions().isBlank()) {
            sb.append("\nInstrucciones:\n").append(prize.getClaimInstructions());
        }
        return sb.toString();
    }
}
