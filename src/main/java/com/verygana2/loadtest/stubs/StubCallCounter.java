package com.verygana2.loadtest.stubs;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Contador {@code loadtest_stub_calls_total{provider}} (email, sms, recaptcha). Se lee en
 * {@code /actuator/prometheus} al terminar la corrida, como evidencia de que ningún
 * tercero real recibió tráfico.
 */
@Component
@Profile("loadtest")
public class StubCallCounter {

    public static final String EMAIL = "email";
    public static final String SMS = "sms";
    public static final String RECAPTCHA = "recaptcha";

    private final MeterRegistry registry;

    public StubCallCounter(MeterRegistry registry) {
        this.registry = registry;
        // Se registran en 0 al arrancar: una serie ausente no distingue "ninguna llamada" de
        // "métrica no expuesta" al revisar /actuator/prometheus (el endpoint exige Bearer token).
        for (String provider : new String[] { EMAIL, SMS, RECAPTCHA }) {
            registry.counter("loadtest.stub.calls", "provider", provider);
        }
    }

    public void count(String provider) {
        registry.counter("loadtest.stub.calls", "provider", provider).increment();
    }

    /** Espera la latencia simulada del proveedor; 0 o negativo no espera. */
    public static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
