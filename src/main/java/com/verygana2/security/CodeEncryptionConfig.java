package com.verygana2.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cada dominio que necesita cifrar códigos tiene su propia instancia de
 * CodeEncryptor con su propia llave, para que comprometer la llave de un
 * dominio no exponga el otro (ver discusión en la PR de códigos de producto).
 */
@Configuration
public class CodeEncryptionConfig {

    @Bean
    public CodeEncryptor claimCodeEncryptor(
            @Value("${app.claim-code.encryption-key}") String aesHexKey) {
        return new CodeEncryptor(aesHexKey, null);
    }

    @Bean
    public CodeEncryptor productCodeEncryptor(
            @Value("${app.product-code.encryption-key}") String aesHexKey,
            @Value("${app.product-code.hmac-secret}") String hmacHexKey) {
        return new CodeEncryptor(aesHexKey, hmacHexKey);
    }
}
