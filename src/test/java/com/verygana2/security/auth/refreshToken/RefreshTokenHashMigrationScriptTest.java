package com.verygana2.security.auth.refreshToken;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Análisis estático de la migración del hash del refresh token. No corre contra MySQL:
 * eso se verifica a mano contra la base local (spec 005, T6).
 */
@DisplayName("Migración refresh_token_hash - análisis estático")
class RefreshTokenHashMigrationScriptTest {

    private static final String FILE_NAME = "V202610021500__refresh_token_hash.sql";
    private static final Pattern PLAIN_VERSION = Pattern.compile("^V(\\d{1,6})__.*\\.sql$");

    private static String rawSql() throws IOException {
        ClassPathResource resource = new ClassPathResource("db/migration/" + FILE_NAME);
        assertThat(resource.exists()).as("falta la migración %s", FILE_NAME).isTrue();
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String sql() throws IOException {
        return rawSql().replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)--.*$", " ");
    }

    @Test
    @DisplayName("el nombre usa versión con fecha y es mayor que la última V<n> correlativa")
    void fileNameUsesTimestampVersion() throws IOException {
        assertThat(FILE_NAME).matches("^V\\d{12}__refresh_token_hash\\.sql$");

        long maxPlain = 0;
        List<String> names = new ArrayList<>();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql")) {
            names.add(r.getFilename());
        }
        for (String name : names) {
            Matcher m = PLAIN_VERSION.matcher(name);
            if (m.matches()) {
                maxPlain = Math.max(maxPlain, Long.parseLong(m.group(1)));
            }
        }
        long version = Long.parseLong(FILE_NAME.substring(1, 13));
        assertThat(version).isGreaterThan(maxPlain);
        assertThat(names).contains(FILE_NAME);
    }

    @Test
    @DisplayName("cada DDL va dentro de un IF(...) sobre information_schema que se prepara y ejecuta")
    void everyDdlIsGuardedByInformationSchema() throws IOException {
        String sql = sql();

        // ADD COLUMN token_hash, ADD UNIQUE INDEX y DROP COLUMN token aparecen solo como texto de @ddl
        for (String ddl : List.of("ADD COLUMN token_hash", "ADD UNIQUE INDEX uk_rt_token_hash", "DROP COLUMN token'")) {
            int at = sql.indexOf(ddl);
            assertThat(at).as("falta %s", ddl).isGreaterThan(0);
            String before = sql.substring(Math.max(0, at - 120), at);
            assertThat(before).as("%s debe ir dentro de SET @ddl := IF(", ddl).contains("IF(@");
        }
        assertThat(sql).contains("information_schema.columns").contains("information_schema.statistics");
        assertThat(sql).contains("PREPARE stmt FROM @ddl").contains("EXECUTE stmt");

        // Fuera del texto de @ddl no hay ALTER TABLE suelto, salvo el MODIFY a NOT NULL (redefinir es idempotente)
        Matcher alter = Pattern.compile("(?i)ALTER\\s+TABLE[^;]*").matcher(sql);
        while (alter.find()) {
            int start = alter.start();
            String prefix = sql.substring(Math.max(0, start - 1), start);
            boolean insideLiteral = prefix.equals("'");
            boolean isModify = alter.group().toUpperCase().contains("MODIFY COLUMN");
            assertThat(insideLiteral || isModify).as("ALTER sin condición: %s", alter.group()).isTrue();
        }
    }

    @Test
    @DisplayName("el único DELETE solo toca filas sin huella")
    void deleteOnlyTouchesRowsWithoutHash() throws IOException {
        Matcher m = Pattern.compile("(?i)DELETE\\s+FROM[^;]*").matcher(sql());
        List<String> deletes = new ArrayList<>();
        while (m.find()) {
            deletes.add(m.group().replaceAll("\\s+", " ").trim());
        }

        assertThat(deletes).hasSize(1);
        assertThat(deletes.get(0)).isEqualToIgnoringCase("DELETE FROM refresh_tokens WHERE token_hash IS NULL");
    }

    @Test
    @DisplayName("no crea tablas, triggers ni funciones (sql_require_primary_key y log_bin_trust_function_creators)")
    void createsNoTablesTriggersOrFunctions() throws IOException {
        assertThat(sql()).doesNotContainPattern("(?i)CREATE\\s+(TEMPORARY\\s+)?TABLE")
                .doesNotContainPattern("(?i)CREATE\\s+(DEFINER\\s*=\\s*\\S+\\s+)?(TRIGGER|FUNCTION|PROCEDURE)")
                .doesNotContainPattern("(?i)DROP\\s+TABLE");
    }

    @Test
    @DisplayName("token_hash es varchar(64) ascii_bin NOT NULL y se elimina la columna token")
    void hashColumnIsAsciiVarchar64NotNull() throws IOException {
        String sql = sql().replaceAll("\\s+", " ");

        assertThat(sql).contains("varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL");
        assertThat(sql).contains("MODIFY COLUMN token_hash varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL");
        assertThat(sql).contains("DROP COLUMN token'");
    }
}
