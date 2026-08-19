package com.verygana2.security.recaptcha;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RecaptchaService {

    private static final String VERIFY_URL =
            "https://www.google.com/recaptcha/api/siteverify";

    private final RestClient restClient;

    @Value("${recaptcha.secret-key}")
    private String secretKey;

    @Value("${recaptcha.min-score:0.5}")
    private double minScore;

    @Value("${recaptcha.login-action:login}")
    private String expectedAction;

    public RecaptchaService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public boolean verify(String token) {

        if (token == null || token.isBlank()) {
            log.warn("reCAPTCHA token is missing");
            return false;
        }

        try {

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();

            body.add("secret", secretKey);
            body.add("response", token);

            Map<String, Object> response = restClient.post()
                    .uri(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                log.warn("Empty response from reCAPTCHA");
                return false;
            }

            Boolean success = (Boolean) response.get("success");

            if (!Boolean.TRUE.equals(success)) {
                log.warn(
                        "reCAPTCHA verification failed. errors={}",
                        response.get("error-codes")
                );
                return false;
            }

            String action = (String) response.get("action");

            if (!expectedAction.equals(action)) {
                log.warn(
                        "Invalid reCAPTCHA action. expected={}, received={}",
                        expectedAction,
                        action
                );
                return false;
            }

            Number score = (Number) response.get("score");

            if (score == null) {
                log.warn("reCAPTCHA response did not contain score");
                return false;
            }

            double scoreValue = score.doubleValue();

            log.debug(
                    "reCAPTCHA verified. action={}, score={}",
                    action,
                    scoreValue
            );

            return scoreValue >= minScore;

        } catch (Exception e) {

            log.error(
                    "Error communicating with Google reCAPTCHA",
                    e
            );

            return false;
        }
    }
}