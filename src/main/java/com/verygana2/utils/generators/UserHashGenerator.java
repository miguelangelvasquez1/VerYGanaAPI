package com.verygana2.utils.generators;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class UserHashGenerator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${games.user-hash-secret}")
    private String SECRET;

    public String generate(Long userId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec =
                    new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);

            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(userId.toString().getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);

        } catch (Exception e) {
            throw new IllegalStateException("Error generating user hash", e);
        }
    }
}
