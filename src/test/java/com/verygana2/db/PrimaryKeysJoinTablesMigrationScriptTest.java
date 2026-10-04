package com.verygana2.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Análisis estático de la migración que agrega la llave primaria a las 8 tablas de unión (spec 003).
 * No corre contra MySQL: eso se verifica a mano contra la base local (tareas T5 a T8).
 */
@DisplayName("Migración primary_keys_join_tables - análisis estático")
class PrimaryKeysJoinTablesMigrationScriptTest {

    private static final String FILE_NAME = "V202610041435__primary_keys_join_tables.sql";
    private static final long PREVIOUS_VERSION = 202610021500L;
    private static final Pattern PLAIN_VERSION = Pattern.compile("^V(\\d{1,6})__.*\\.sql$");
    private static final String ALTER_BODY = "ADD COLUMN id bigint NOT NULL AUTO_INCREMENT INVISIBLE PRIMARY KEY";
    private static final Pattern ALTER = Pattern.compile("'ALTER TABLE (\\w+) ([^']*)'", Pattern.CASE_INSENSITIVE);

    private static final List<String> TABLES = List.of(
            "answer_selected_options",
            "consumer_preferences",
            "target_audience_categories",
            "target_audience_municipalities",
            "asset_definition_mime_types",
            "commercial_onboarding_institutional_tools",
            "commercial_onboarding_network_actors",
            "commercial_onboarding_tech_needs");

    private static String sql() throws IOException {
        ClassPathResource resource = new ClassPathResource("db/migration/" + FILE_NAME);
        assertThat(resource.exists()).as("falta la migración %s", FILE_NAME).isTrue();
        String raw = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return raw.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)--.*$", " ");
    }

    /** Los ALTER TABLE de la migración, tabla -> cuerpo; todos van dentro de un literal. */
    private static Map<String, String> alters() throws IOException {
        Map<String, String> result = new TreeMap<>();
        Matcher m = ALTER.matcher(sql());
        while (m.find()) {
            result.put(m.group(1), m.group(2));
        }
        return result;
    }

    @Test
    @DisplayName("el nombre usa versión con fecha y es posterior a refresh_token_hash")
    void fileNameUsesTimestampVersionAfterRefreshTokenHash() throws IOException {
        assertThat(FILE_NAME).matches("^V\\d{12}__primary_keys_join_tables\\.sql$");

        long maxPlain = 0;
        List<String> names = new ArrayList<>();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql")) {
            names.add(r.getFilename());
            Matcher m = PLAIN_VERSION.matcher(r.getFilename());
            if (m.matches()) {
                maxPlain = Math.max(maxPlain, Long.parseLong(m.group(1)));
            }
        }
        long version = Long.parseLong(FILE_NAME.substring(1, 13));
        assertThat(version).isGreaterThan(PREVIOUS_VERSION).isGreaterThan(maxPlain);
        assertThat(names).contains(FILE_NAME);
    }

    @Test
    @DisplayName("cubre exactamente las 8 tablas de la spec, una vez cada una")
    void coversExactlyTheEightTablesOfTheSpec() throws IOException {
        List<String> found = new ArrayList<>();
        Matcher m = Pattern.compile("(?i)ALTER TABLE (\\w+)").matcher(sql());
        while (m.find()) {
            found.add(m.group(1));
        }
        assertThat(found).containsExactlyInAnyOrderElementsOf(TABLES);
    }

    @Test
    @DisplayName("cada tabla recibe columna y llave en una sola sentencia, sin ADD PRIMARY KEY suelto")
    void addsColumnAndKeyInASingleStatementPerTable() throws IOException {
        Map<String, String> alters = alters();
        assertThat(alters).hasSize(8);
        alters.forEach((table, body) -> assertThat(body).as("ALTER de %s", table).isEqualTo(ALTER_BODY));
        assertThat(sql()).doesNotContainPattern("(?i)ADD\\s+PRIMARY\\s+KEY");
    }

    @Test
    @DisplayName("cada ALTER va dentro de un IF(...) que consulta la llave en information_schema y se prepara y ejecuta")
    void everyAlterIsGuardedByPrimaryKeyCheck() throws IOException {
        String sql = sql();
        // Ningún ALTER fuera de un literal entre comillas simples
        assertThat(sql.replaceAll("'[^']*'", "''")).doesNotContainPattern("(?i)ALTER\\s+TABLE");

        for (String table : TABLES) {
            String block = Pattern.compile("(?s)SET @needs_pk := \\(.*?DEALLOCATE PREPARE stmt;")
                    .matcher(sql).results()
                    .map(r -> r.group())
                    .filter(b -> b.contains("table_name = '" + table + "'"))
                    .findFirst().orElse("");
            assertThat(block).as("bloque de %s", table).isNotEmpty();
            assertThat(block).contains("information_schema.table_constraints", "constraint_type = 'PRIMARY KEY'",
                    "IF(@needs_pk = 1,", "'ALTER TABLE " + table + " ", "PREPARE stmt FROM @ddl;", "EXECUTE stmt;");
        }
        assertThat(Pattern.compile("PREPARE stmt FROM").matcher(sql).results().count()).isEqualTo(8);
    }

    @Test
    @DisplayName("nunca borra ni modifica filas ni columnas existentes")
    void neverDeletesOrUpdatesRows() throws IOException {
        assertThat(sql()).doesNotContainPattern(
                "(?i)\\b(DELETE|UPDATE|TRUNCATE|INSERT|DROP|MODIFY|CHANGE|RENAME)\\b");
    }

    @Test
    @DisplayName("solo agrega la llave sustituta: sin UNIQUE, sin llave compuesta, sin NOT NULL sobre columnas existentes")
    void addsOnlyASurrogateKey() throws IOException {
        String sql = sql();
        assertThat(sql).doesNotContainPattern("(?i)\\b(UNIQUE|INDEX|KEY\\s*\\()");
        assertThat(sql).doesNotContainPattern("(?i)PRIMARY\\s+KEY\\s*\\(");
        // El único NOT NULL es el de la columna id nueva (uno por ALTER)
        assertThat(Pattern.compile("(?i)NOT NULL").matcher(sql).results().count()).isEqualTo(8);
        assertThat(Pattern.compile("(?i)ADD COLUMN id bigint NOT NULL").matcher(sql).results().count()).isEqualTo(8);
    }

    @Test
    @DisplayName("no crea tablas, triggers, funciones ni procedimientos (usuario sin SUPER)")
    void createsNoTablesTriggersOrFunctions() throws IOException {
        assertThat(sql()).doesNotContainPattern("(?i)\\bCREATE\\b");
    }

    @Test
    @DisplayName("ningún seed inserta en las 8 tablas nombrando la columna id")
    void seedInsertsNeverWriteTheSurrogateKey() throws IOException {
        Pattern insert = Pattern.compile(
                "(?is)INSERT\\s+(?:IGNORE\\s+)?INTO\\s+`?(" + String.join("|", TABLES) + ")`?\\s*\\(([^)]*)\\)");
        List<String> offenders = new ArrayList<>();
        List<Resource> files = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        files.addAll(List.of(resolver.getResources("classpath:db/seed/**/*.sql")));
        files.addAll(List.of(resolver.getResources("classpath:db/loadtest/*.sql")));
        assertThat(files).isNotEmpty();

        for (Resource r : files) {
            String text = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)--.*$", " ");
            Matcher m = insert.matcher(text);
            while (m.find()) {
                if (Pattern.compile("(?i)(^|[\\s,`])id([\\s,`]|$)").matcher(m.group(2)).find()) {
                    offenders.add(r.getFilename() + " -> " + m.group(1));
                }
            }
        }
        assertThat(offenders).as("seeds que escriben la columna id").isEmpty();
    }
}
