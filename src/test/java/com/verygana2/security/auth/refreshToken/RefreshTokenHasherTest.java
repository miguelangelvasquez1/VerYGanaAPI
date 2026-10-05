package com.verygana2.security.auth.refreshToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RefreshTokenHasher - huella SHA-256 del refresh token")
class RefreshTokenHasherTest {

    private static final String SAMPLE_JWT =
            "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyQHRlc3QuY29tIiwidHlwZSI6InJlZnJlc2gifQ.firma-de-ejemplo";

    @Test
    @DisplayName("coincide con el vector conocido de SHA-256 para \"abc\"")
    void matchesKnownSha256Vector() {
        assertThat(RefreshTokenHasher.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("es determinista: el mismo token da siempre la misma huella")
    void isDeterministic() {
        assertThat(RefreshTokenHasher.hash(SAMPLE_JWT)).isEqualTo(RefreshTokenHasher.hash(SAMPLE_JWT));
    }

    @Test
    @DisplayName("devuelve 64 caracteres hexadecimales en minúscula")
    void returns64LowercaseHexChars() {
        assertThat(RefreshTokenHasher.hash(SAMPLE_JWT)).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("tokens distintos dan huellas distintas")
    void differentTokensGiveDifferentHashes() {
        assertThat(RefreshTokenHasher.hash(SAMPLE_JWT)).isNotEqualTo(RefreshTokenHasher.hash(SAMPLE_JWT + "x"));
    }

    @Test
    @DisplayName("la huella no contiene el token ni su prefijo JWT")
    void neverContainsTheInput() {
        String hash = RefreshTokenHasher.hash(SAMPLE_JWT);

        assertThat(hash).doesNotContain(SAMPLE_JWT).doesNotContain("eyJ");
    }

    @Test
    @DisplayName("null lanza NullPointerException con un mensaje sin datos del token")
    void rejectsNullWithoutLeakingData() {
        assertThatThrownBy(() -> RefreshTokenHasher.hash(null))
                .isInstanceOf(NullPointerException.class)
                .message().doesNotContain("eyJ");
    }
}
