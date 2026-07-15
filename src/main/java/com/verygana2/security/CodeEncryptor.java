package com.verygana2.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.verygana2.exceptions.CodeEncryptionException;

/**
 * Cifra/descifra códigos sensibles (códigos de reclamo de premios, licencias
 * de productos, etc.) con AES-GCM, y opcionalmente calcula un HMAC-SHA256
 * determinístico del texto plano para permitir búsquedas de igualdad/dedup
 * sin necesidad de descifrar.
 *
 * Cada dominio (premios, productos) debe usar una instancia propia con su
 * propia llave — ver CodeEncryptionConfig — para que comprometer una llave
 * no exponga el otro dominio.
 */
public class CodeEncryptor {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec aesKeySpec;
    private final SecretKeySpec hmacKeySpec;

    public CodeEncryptor(String aesHexKey, String hmacHexKey) {
        this.aesKeySpec = new SecretKeySpec(parseKey(aesHexKey, "AES encryption key"), "AES");
        this.hmacKeySpec = hmacHexKey == null
                ? null
                : new SecretKeySpec(parseKey(hmacHexKey, "HMAC key"), HMAC_ALGORITHM);
    }

    private static byte[] parseKey(String hexKey, String label) {
        byte[] keyBytes = HexFormat.of().parseHex(hexKey);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(label + " must be a 64-char hex string (256-bit key)");
        }
        return keyBytes;
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKeySpec, new GCMParameterSpec(TAG_BITS, iv));
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
            cipher.init(Cipher.DECRYPT_MODE, aesKeySpec, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CodeEncryptionException("Code decryption failed", e);
        }
    }

    /**
     * HMAC-SHA256 determinístico del texto plano, en hex. Mismo texto plano
     * siempre produce el mismo hash, por lo que sirve para detectar
     * duplicados o buscar coincidencias exactas sin descifrar nada.
     * No es reversible.
     */
    public String hash(String plaintext) {
        if (hmacKeySpec == null) {
            throw new IllegalStateException("This CodeEncryptor instance has no HMAC key configured");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hmacKeySpec);
            byte[] digest = mac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new CodeEncryptionException("Code hashing failed", e);
        }
    }
}
