package com.verygana2.loadtest.stubs;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.verygana2.loadtest.LoadTestProperties;
import com.verygana2.models.raffles.Prize;
import com.verygana2.services.interfaces.TwilioSmsService;

/**
 * Twilio falso de la prueba de carga. {@code sendOtp} y {@code sendPrizeClaimConfirmation}
 * solo esperan la latencia configurada y suman al contador; {@code verifyOtp} acepta
 * únicamente {@code loadtest.stubs.otp-code}. No escribe teléfonos ni códigos en el log.
 */
@Service
@Primary
@Profile("loadtest")
public class LoadTestSmsService implements TwilioSmsService {

    private final LoadTestProperties properties;
    private final StubCallCounter counter;

    public LoadTestSmsService(LoadTestProperties properties, StubCallCounter counter) {
        this.properties = properties;
        this.counter = counter;
    }

    @Override
    public void sendOtp(String phoneNumber) {
        simulate();
    }

    @Override
    public boolean verifyOtp(String phoneNumber, String code) {
        String expected = properties.getStubs().getOtpCode();
        return code != null && !code.isBlank() && code.equals(expected);
    }

    @Override
    public void sendPrizeClaimConfirmation(Prize prize, String phoneNumber, String decryptedClaimCode) {
        simulate();
    }

    private void simulate() {
        StubCallCounter.sleep(properties.getStubs().getSmsLatencyMs());
        counter.count(StubCallCounter.SMS);
    }
}
