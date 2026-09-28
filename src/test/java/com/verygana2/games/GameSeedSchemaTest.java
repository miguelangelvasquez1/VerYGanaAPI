package com.verygana2.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameConfigDefinition;
import com.verygana2.utils.games.BriefSchemaExtractor;
import com.verygana2.utils.games.GameBriefCatalog;
import com.verygana2.utils.games.GameBriefValidator;
import com.verygana2.utils.validators.games.GameConfigValidator;
import com.verygana2.utils.validators.games.SchemaValidator;

import jakarta.validation.ValidationException;

/**
 * Los esquemas sembrados, leídos como los lee la aplicación.
 *
 * Un {@code json_schema} que no parsea no falla en el build ni en ningún test de
 * unidad: falla al arrancar, cuando {@code DataSeeder} corre el script, y con un
 * error de MySQL que no nombra el archivo. Estos tests lo atrapan antes.
 */
class GameSeedSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> BOGOTA = List.of(
        "dash-runner", "endless-runner", "endless-runner-v2", "memory-match", "mini-flappy",
        "simple-crossword", "simple-crossword-v2", "stack-tower", "tic-tac-toe", "tile-puzzle",
        "trivia-quiz", "trivia-quiz-v2", "word-search", "word-search-v2");

    private static final List<String> CALI = List.of(
        "avoid-the-bomb", "ball-bounce", "ball-bounce-v2", "balloon-lift", "catch-it", "hangman",
        "hangman-v2", "match3", "memory", "sudoku", "tap-to-rotate", "whack-a-mole");

    /** Las cadenas entre comillas simples de un script SQL, respetando '' como escape. */
    private static List<String> sqlStrings(String sql) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < sql.length()) {
            if (sql.charAt(i) == '\'') {
                StringBuilder buf = new StringBuilder();
                int j = i + 1;
                while (j < sql.length()) {
                    if (sql.charAt(j) == '\'') {
                        if (j + 1 < sql.length() && sql.charAt(j + 1) == '\'') {
                            buf.append('\''); j += 2; continue;
                        }
                        break;
                    }
                    buf.append(sql.charAt(j)); j++;
                }
                out.add(buf.toString());
                i = j + 1;
            } else {
                i++;
            }
        }
        return out;
    }

    private static String seed(String city, String slug) throws IOException {
        try (InputStream in = GameSeedSchemaTest.class.getClassLoader()
                .getResourceAsStream("db/seed/games/" + city + "/" + slug + ".sql")) {
            assertThat(in).as("seed %s/%s", city, slug).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> jsonBlocks(String sql) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (String s : sqlStrings(sql)) {
            String trimmed = s.trim();
            if (!trimmed.startsWith("{")) continue;
            try {
                blocks.add(MAPPER.readValue(trimmed, Map.class));
            } catch (Exception e) {
                throw new AssertionError("JSON inválido en el seed: " + e.getMessage(), e);
            }
        }
        return blocks;
    }

    @Test
    @DisplayName("todos los seeds traen un json_schema y un ui_schema que parsean")
    void everySeedParses() throws IOException {
        for (String slug : BOGOTA) assertBlocks("bogota", slug);
        for (String slug : CALI) assertBlocks("cali", slug);
    }

    private void assertBlocks(String city, String slug) throws IOException {
        List<Map<String, Object>> blocks = jsonBlocks(seed(city, slug));

        assertThat(blocks)
            .as("bloques JSON de %s/%s", city, slug)
            .hasSize(2);
        assertThat(blocks.get(0))
            .as("json_schema de %s/%s", city, slug)
            .containsKey("properties");
    }

    @Test
    @DisplayName("ball bounce v2 declara initial_lives, que el build lee y la v1 no tenía")
    @SuppressWarnings("unchecked")
    void ballBounceV2DeclaresInitialLives() throws IOException {
        // Sin este campo el JSON llega sin vidas, en C# queda en 0 y la partida termina
        // apenas arranca. El build lo declara en BallBounce.Data|GameConfigData.
        Map<String, Object> schema = jsonBlocks(seed("cali", "ball-bounce-v2")).get(0);

        Map<String, Object> gameConfig = (Map<String, Object>)
            ((Map<String, Object>) schema.get("properties")).get("game_config");
        Map<String, Object> initialLives = (Map<String, Object>)
            ((Map<String, Object>) gameConfig.get("properties")).get("initial_lives");

        assertThat(initialLives)
            .containsEntry("type", "integer")
            .containsEntry("default", 3)
            .containsEntry("minimum", 1);
        assertThat((List<String>) gameConfig.get("required")).contains("initial_lives");
    }

    @Test
    @DisplayName("sopa de letras v2: cada palabra tiene como máximo 10 letras")
    void wordSearchWordsFitTheBoard() throws IOException {
        // Con el esquema real del seed y el validador real del brief: una palabra más
        // larga que el tablero no se puede colocar y el build la descarta sin avisar.
        Map<String, Object> schema = jsonBlocks(seed("bogota", "word-search-v2")).get(0);
        Game wordSearch = Game.builder()
            .id(20L).title("Word Search").url("word-search")
            .configDefinitions(new ArrayList<>(List.of(
                GameConfigDefinition.builder().version(2L).jsonSchema(schema).build())))
            .build();

        SchemaValidator schemaValidator = new SchemaValidator(MAPPER);
        GameBriefValidator validator = new GameBriefValidator(schemaValidator,
            new GameConfigValidator(schemaValidator), new GameBriefCatalog(), new BriefSchemaExtractor());

        assertThatCode(() -> validator.validateOrThrow(wordSearch, words("MANGOSDULC")))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateOrThrow(wordSearch, words("CHOCOLATERA")))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("endless runner v2: ninguna de las dos llaves admite 0")
    @SuppressWarnings("unchecked")
    void endlessRunnerKeysCannotBeZero() throws IOException {
        // Con cualquiera de los dos en 0, el build descarta la config entera y arranca
        // sin brandeo. La v1 los permitía y además los pre-llenaba en 0.
        Map<String, Object> schema = jsonBlocks(seed("bogota", "endless-runner-v2")).get(0);
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        Map<String, Object> probability = (Map<String, Object>) ((Map<String, Object>)
            ((Map<String, Object>) props.get("game")).get("properties")).get("key_spawn_probability");
        Map<String, Object> keys = (Map<String, Object>) ((Map<String, Object>)
            ((Map<String, Object>) props.get("rewards")).get("properties")).get("keys_per_action");

        assertThat(probability).containsEntry("default", 0.3);
        assertThat(keys).containsEntry("default", 1);

        SchemaValidator validator = new SchemaValidator(MAPPER);
        assertThat(validator.validate(Map.of("v", 0.0), field(probability))).isNotEmpty();
        assertThat(validator.validate(Map.of("v", 0.3), field(probability))).isEmpty();
        assertThat(validator.validate(Map.of("v", 0), field(keys))).isNotEmpty();
        assertThat(validator.validate(Map.of("v", 1), field(keys))).isEmpty();
    }

    /** Un esquema de un solo campo, para validar esa regla sin armar la config entera. */
    private static Map<String, Object> field(Map<String, Object> definition) {
        return Map.of("type", "object", "required", List.of("v"), "properties", Map.of("v", definition));
    }

    /** Diez palabras válidas, la última reemplazada por la que se prueba. */
    private static Map<String, Object> words(String last) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 1; i < 10; i++) list.add(Map.of("word", "MARCA" + i, "color", "#FFAA00"));
        list.add(Map.of("word", last, "color", "#FFAA00"));
        return Map.of("game", Map.of("words", list));
    }

    @Test
    @DisplayName("hangman v2 se queda sin el default de palabras en inglés")
    @SuppressWarnings("unchecked")
    void hangmanV2HasNoDefaultWords() throws IOException {
        // El default traía «SUN AND MOON», «MOON» y «BLUE SKY»: pre-llenaba el
        // formulario y pasaba la validación, así que una campaña podía salir con tres
        // palabras en inglés sobre astronomía que nadie eligió.
        Map<String, Object> schema = jsonBlocks(seed("cali", "hangman-v2")).get(0);

        Map<String, Object> words = (Map<String, Object>) ((Map<String, Object>)
            ((Map<String, Object>) ((Map<String, Object>)
                schema.get("properties")).get("game")).get("properties")).get("words");

        assertThat(words).doesNotContainKey("default");
        assertThat(words).containsEntry("title", "Palabras");
    }

    @Test
    @DisplayName("la v2 se inserta como versión 2 y jubila a la 1")
    void v2IsVersionTwo() throws IOException {
        String sql = seed("bogota", "trivia-quiz-v2").replaceAll("\\s+", " ");

        // latestDefinition() elige por max(version): si la v2 entrara con version = 1,
        // el INSERT no haría nada y los cambios no llegarían a ningún lado.
        assertThat(sql).contains("WHERE game_id = 19 AND version = 2");
        assertThat(sql).contains("UPDATE game_config_definitions SET is_latest = false WHERE game_id = 19 AND version = 1");
    }

    @Test
    @DisplayName("los seeds v2 solo actúan si el id es de verdad ese juego")
    void v2IsGuardedByGameUrl() throws IOException {
        // Los ids del seed son fijos. En una base sembrada de otra forma, un id podría
        // ser de otro juego y el script le cambiaría el esquema sin error.
        Map<String, String> slugPorSeed = Map.of(
            "bogota/trivia-quiz-v2", "id = 19 AND url = 'trivia-quiz'",
            "bogota/word-search-v2", "id = 20 AND url = 'word-search'",
            "bogota/simple-crossword-v2", "id = 15 AND url = 'simple-crossword'",
            "cali/ball-bounce-v2", "id = 2 AND url = 'Ball%20Bounce'",
            "cali/hangman-v2", "id = 5 AND url = 'Hangman'",
            "bogota/endless-runner-v2", "id = 12 AND url = 'endless-runner'");

        for (var entry : slugPorSeed.entrySet()) {
            String[] parts = entry.getKey().split("/");
            String sql = seed(parts[0], parts[1]).replaceAll("\\s+", " ");
            String guard = "EXISTS (SELECT 1 FROM games WHERE " + entry.getValue() + ")";

            // Una vez en el UPDATE que jubila la v1 y otra en el INSERT de la v2.
            assertThat(sql.split(java.util.regex.Pattern.quote(guard), -1))
                .as("guarda de %s", entry.getKey())
                .hasSize(3);
        }
    }

    @Test
    @DisplayName("el bloque que llena el anunciante está en español")
    @SuppressWarnings("unchecked")
    void briefBlockIsInSpanish() throws IOException {
        // Ese formulario lo veía solo el diseñador; desde que lo llena la marca, un
        // "zero-based index" no le dice nada a nadie.
        Map<String, Object> schema = jsonBlocks(seed("bogota", "trivia-quiz-v2")).get(0);

        Map<String, Object> questions = (Map<String, Object>)
            ((Map<String, Object>) ((Map<String, Object>)
                ((Map<String, Object>) schema.get("properties")).get("game")).get("properties")).get("questions");

        assertThat(questions).containsEntry("title", "Preguntas");

        Map<String, Object> correct = (Map<String, Object>)
            ((Map<String, Object>) ((Map<String, Object>) questions.get("items")).get("properties"))
                .get("correct_answer_index");

        // El valor sigue contándose desde 0: la ayuda tiene que decirlo con ejemplos,
        // no traducir "zero-based" y dejar al anunciante adivinando.
        assertThat((String) correct.get("description"))
            .contains("0 si la correcta es la primera");
    }

    @Test
    @DisplayName("trivia v2 exige exactamente 4 opciones: el build dibuja cuatro botones")
    @SuppressWarnings("unchecked")
    void triviaRequiresExactlyFourOptions() throws IOException {
        // Con 2 opciones el juego no renderiza ninguna pregunta y cae a su contenido de
        // ejemplo, sin error. El esquema permitía de 2 a 6, así que el backend dejaba
        // pasar campañas que el juego no puede mostrar.
        Map<String, Object> schema = jsonBlocks(seed("bogota", "trivia-quiz-v2")).get(0);

        Map<String, Object> item = (Map<String, Object>) ((Map<String, Object>)
            ((Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>)
                schema.get("properties")).get("game")).get("properties")).get("questions")).get("items");
        Map<String, Object> props = (Map<String, Object>) item.get("properties");

        assertThat((Map<String, Object>) props.get("options"))
            .containsEntry("minItems", 4)
            .containsEntry("maxItems", 4);

        // Y el índice de la correcta tiene que moverse con el rango: con maximum 5 se
        // podía señalar una quinta opción que no existe.
        assertThat((Map<String, Object>) props.get("correct_answer_index"))
            .containsEntry("maximum", 3);
    }

    @Test
    @DisplayName("la v2 conserva los bloques y los required de la v1")
    @SuppressWarnings("unchecked")
    void translationDoesNotChangeValidation() throws IOException {
        // Lo que cambia entre v1 y v2 es la redacción y el rango de opciones. Los
        // bloques y lo obligatorio no: si se colara un required distinto, el diseñador
        // vería errores que no puede corregir.
        for (String slug : List.of("trivia-quiz", "word-search", "simple-crossword")) {
            Map<String, Object> v1 = jsonBlocks(seed("bogota", slug)).get(0);
            Map<String, Object> v2 = jsonBlocks(seed("bogota", slug + "-v2")).get(0);

            assertThat(requiredOfGame(v2)).as("required de %s", slug).isEqualTo(requiredOfGame(v1));
            assertThat(((Map<String, Object>) v2.get("properties")).keySet())
                .as("bloques de %s", slug)
                .containsExactlyInAnyOrderElementsOf(((Map<String, Object>) v1.get("properties")).keySet());
        }
    }

    @SuppressWarnings("unchecked")
    private static Object requiredOfGame(Map<String, Object> schema) {
        return ((Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("game")).get("required");
    }

    @Test
    @DisplayName("la v2 conserva el resto del esquema de la v1")
    @SuppressWarnings("unchecked")
    void v2KeepsTheRestOfTheSchema() throws IOException {
        // Es un esquema completo, no un parche: si se perdiera un bloque, el formulario
        // del diseñador dejaría de pedirlo y la campaña saldría sin él.
        Map<String, Object> v1 = jsonBlocks(seed("bogota", "trivia-quiz")).get(0);
        Map<String, Object> v2 = jsonBlocks(seed("bogota", "trivia-quiz-v2")).get(0);

        assertThat(((Map<String, Object>) v2.get("properties")).keySet())
            .containsExactlyInAnyOrderElementsOf(((Map<String, Object>) v1.get("properties")).keySet());
    }
}
