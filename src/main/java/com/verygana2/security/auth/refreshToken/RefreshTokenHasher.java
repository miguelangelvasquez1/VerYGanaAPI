package com.verygana2.security.auth.refreshToken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Huella SHA-256 (hex minúscula, 64 caracteres) de un refresh token.
 *
 * <p>La base guarda solo la huella, nunca el JWT. Es determinista y sin sal a propósito:
 * el token ya es un JWT firmado con un {@code jti} aleatorio, así que no se puede invertir
 * por diccionario, y la búsqueda por índice ({@code WHERE token_hash = ?}) exige que el mismo
 * token dé siempre la misma huella. Sin estado y sin logs: el valor nunca sale de aquí.
 */
public final class RefreshTokenHasher {

    private RefreshTokenHasher() {
    }

    public static String hash(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken must not be null");
        try {
            // MessageDigest no es thread-safe: se crea por llamada.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
