package com.verygana2.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.verygana2.exceptions.CodeEncryptionException;

@Component
public class ClaimCodeEncryptor {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec keySpec;

    public ClaimCodeEncryptor(@Value("${app.claim-code.encryption-key}") String hexKey) {
        byte[] keyBytes = HexFormat.of().parseHex(hexKey);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "app.claim-code.encryption-key must be a 64-char hex string (256-bit key)");
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, combined, IV_BYTES, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new CodeEncryptionException("Code encryption failed", e);
        }
    }

    public String decrypt(String base64Ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(base64Ciphertext);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_BYTES, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CodeEncryptionException("Code decryption failed", e);
        }
    }
}
