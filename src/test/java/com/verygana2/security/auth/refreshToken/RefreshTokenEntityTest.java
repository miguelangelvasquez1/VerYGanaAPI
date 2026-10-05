package com.verygana2.security.auth.refreshToken;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;

@DisplayName("RefreshToken - la entidad no guarda el token en claro")
class RefreshTokenEntityTest {

    @Test
    @DisplayName("no existe campo ni columna 'token': solo token_hash")
    void hasNoRawTokenField() {
        assertThat(Arrays.stream(RefreshToken.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("token")
                .contains("tokenHash");

        for (Field f : RefreshToken.class.getDeclaredFields()) {
            Column column = f.getAnnotation(Column.class);
            if (column != null) {
                assertThat(column.name()).isNotEqualTo("token");
            }
        }
        Column hashColumn = field("tokenHash").getAnnotation(Column.class);
        assertThat(hashColumn.name()).isEqualTo("token_hash");
        assertThat(hashColumn.length()).isEqualTo(64);
        assertThat(hashColumn.nullable()).isFalse();
    }

    @Test
    @DisplayName("toString() no incluye la huella")
    void toStringDoesNotIncludeHash() {
        String hash = RefreshTokenHasher.hash("cualquier-token");
        RefreshToken rt = new RefreshToken("user@test.com", hash, "jti-1", Instant.now(), "127.0.0.1", "junit");

        assertThat(rt.toString()).doesNotContain(hash);
    }

    private static Field field(String name) {
        try {
            return RefreshToken.class.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new AssertionError("falta el campo " + name, e);
        }
    }
}
