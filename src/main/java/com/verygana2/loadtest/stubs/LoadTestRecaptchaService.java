package com.verygana2.loadtest.stubs;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.verygana2.security.recaptcha.RecaptchaService;

/**
 * reCAPTCHA falso de la prueba de carga: acepta cualquier token no vacío (k6 manda
 * {@code loadtest-placeholder}) sin salir a la red. Es una subclase y no una interfaz
 * nueva para no tocar {@code AuthController} ni {@code RecaptchaService}.
 *
 * <p>El {@code @NotBlank} de los DTO se mantiene; aquí un token vacío sigue rechazado.
 */
@Component
@Primary
@Profile("loadtest")
public class LoadTestRecaptchaService extends RecaptchaService {

    private final StubCallCounter counter;

    public LoadTestRecaptchaService(RestClient.Builder restClientBuilder, StubCallCounter counter) {
        super(restClientBuilder);
        this.counter = counter;
    }

    @Override
    public boolean verify(String token, String expectedAction) {
        counter.count(StubCallCounter.RECAPTCHA);
        return token != null && !token.isBlank();
    }
}
