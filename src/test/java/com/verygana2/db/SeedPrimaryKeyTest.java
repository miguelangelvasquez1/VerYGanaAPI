package com.verygana2.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Complemento de {@link MigrationPrimaryKeyTest} para los seeds (spec 006, T12). DigitalOcean Managed MySQL
 * trae {@code sql_require_primary_key = ON}, y la regla aplica también a las tablas TEMPORALES: un seed que
 * crea una tabla temporal sin llave tumba el arranque del perfil beta, aunque en dev (MySQL local sin la
 * regla) no se note. Lee todos los {@code .sql} de {@code db/seed/} y {@code db/loadtest/}; es análisis de
 * texto, no toca base de datos.
 */
@DisplayName("Llaves primarias en los seeds SQL")
class SeedPrimaryKeyTest {

    private static final List<Path> ROOTS = List.of(
            Path.of("src/main/resources/db/seed"),
            Path.of("src/main/resources/db/loadtest"));

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "create\\s+(?:temporary\\s+)?table\\s+(?:if\\s+not\\s+exists\\s+)?`?(\\w+)`?\\s*(\\(|like\\b|as\\b|select\\b)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIMARY_KEY = Pattern.compile("primary\\s+key", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("ninguna tabla (ni temporal) creada por los seeds queda sin llave primaria")
    void noSeedCreatesTableWithoutPrimaryKey() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path root : ROOTS) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".sql")).sorted().toList()) {
                    String sql = Files.readString(file, StandardCharsets.UTF_8);
                    for (String table : tablesWithoutKey(sql)) {
                        offenders.add(file + " -> " + table);
                    }
                }
            }
        }

        assertThat(offenders)
                .as("""
                        Seeds que crean una tabla sin PRIMARY KEY (archivo -> tabla). DigitalOcean Managed MySQL \
                        tiene sql_require_primary_key = ON y la regla aplica también a las tablas TEMPORARY: \
                        el CREATE falla con 'Unable to create or change a table without a primary key' y la API \
                        de beta no arranca. Agrega una PRIMARY KEY a la tabla (CREATE ... LIKE / AS SELECT no \
                        pueden declararla: define las columnas a mano).""")
                .isEmpty();
    }

    @Test
    @DisplayName("el detector marca tablas normales y temporales sin llave, con o sin IF NOT EXISTS")
    void detectorFlagsTablesWithoutKey() {
        assertThat(tablesWithoutKey("CREATE TABLE a (x int);")).containsExactly("a");
        assertThat(tablesWithoutKey("create temporary table `b` (x int, y enum('A(','B)'));")).containsExactly("b");
        assertThat(tablesWithoutKey("CREATE TEMPORARY TABLE IF NOT EXISTS c (x int);")).containsExactly("c");
    }

    @Test
    @DisplayName("el detector marca CREATE ... LIKE y AS SELECT, que no pueden declarar llave")
    void detectorFlagsLikeAndAsSelect() {
        assertThat(tablesWithoutKey("CREATE TEMPORARY TABLE a LIKE b;")).containsExactly("a");
        assertThat(tablesWithoutKey("CREATE TEMPORARY TABLE a AS SELECT 1;")).containsExactly("a");
        assertThat(tablesWithoutKey("CREATE TABLE a SELECT 1;")).containsExactly("a");
    }

    @Test
    @DisplayName("el detector acepta la llave en la columna o a nivel de tabla y ignora comentarios")
    void detectorAcceptsKeysAndIgnoresComments() {
        assertThat(tablesWithoutKey("CREATE TEMPORARY TABLE a (id int NOT NULL PRIMARY KEY, y int);")).isEmpty();
        assertThat(tablesWithoutKey("CREATE TEMPORARY TABLE a (x int, y int, PRIMARY KEY (x, y));")).isEmpty();
        assertThat(tablesWithoutKey("-- CREATE TABLE a (x int);\n/* CREATE TABLE b (x int); */ SELECT 1;")).isEmpty();
    }

    private static List<String> tablesWithoutKey(String rawSql) {
        String sql = stripComments(rawSql);
        List<String> result = new ArrayList<>();
        Matcher m = CREATE_TABLE.matcher(sql);
        while (m.find()) {
            boolean hasKey = "(".equals(m.group(2)) && PRIMARY_KEY.matcher(balancedBody(sql, m.end() - 1)).find();
            if (!hasKey) {
                result.add(m.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    /** Quita comentarios {@code -- ...}, {@code # ...} y bloque fuera de los literales. */
    private static String stripComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        char quote = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (quote != 0) {
                out.append(c);
                if (c == '\\' && i + 1 < sql.length()) {
                    out.append(sql.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                }
                i++;
            } else if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                out.append(c);
                i++;
            } else if (sql.startsWith("--", i) || c == '#') {
                while (i < sql.length() && sql.charAt(i) != '\n') {
                    i++;
                }
            } else if (sql.startsWith("/*", i)) {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? sql.length() : end + 2;
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Texto entre el paréntesis de {@code openIdx} y su pareja, contando paréntesis fuera de literales. */
    private static String balancedBody(String sql, int openIdx) {
        int depth = 0;
        char quote = 0;
        for (int i = openIdx; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"' || c == '`') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return sql.substring(openIdx + 1, i);
            }
        }
        return sql.substring(openIdx + 1);
    }
}
