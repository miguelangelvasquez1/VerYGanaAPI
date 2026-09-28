package com.verygana2.utils.games;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameConfigDefinition;
import com.verygana2.utils.validators.games.GameConfigValidator;
import com.verygana2.utils.validators.games.SchemaValidator;

import jakarta.validation.ValidationException;

/**
 * La validación del contenido que manda el anunciante, con el motor de esquemas real.
 *
 * Lo que se prueba acá: el juego no necesita «al menos una» pregunta, necesita
 * diez. El {@code json_schema} sembrado dice {@code minItems: 1} porque describe el
 * formulario del diseñador; el mínimo real sale de las configuraciones con las que
 * se probaron los builds (10 preguntas en la trivia, 25 cartas en el memory match).
 */
class GameBriefValidatorTest {

    private final SchemaValidator schemaValidator = new SchemaValidator(new ObjectMapper());
    private final GameConfigValidator configValidator = new GameConfigValidator(schemaValidator);
    private final GameBriefCatalog catalog = new GameBriefCatalog();
    private final GameBriefValidator validator =
        new GameBriefValidator(schemaValidator, configValidator, catalog, new BriefSchemaExtractor());

    /** El bloque de preguntas tal como lo declara el seed de trivia-quiz. */
    private static Game triviaQuiz() {
        Map<String, Object> schema = Map.of(
            "type", "object",
            "required", List.of("meta", "game"),
            "properties", Map.of(
                "meta", Map.of("type", "object", "properties", Map.of(
                    "brand_id", Map.of("type", "string"))),
                "game", Map.of(
                    "type", "object",
                    "required", List.of("questions"),
                    "properties", Map.of(
                        "questions", Map.of(
                            "type", "array",
                            "minItems", 1,
                            "items", Map.of(
                                "type", "object",
                                "required", List.of("id", "question", "options", "correct_answer_index"),
                                "properties", Map.of(
                                    "id", Map.of("type", "integer", "minimum", 1),
                                    "question", Map.of("type", "string", "minLength", 1, "maxLength", 512),
                                    "options", Map.of("type", "array", "minItems", 2, "maxItems", 6,
                                        "items", Map.of("type", "string", "minLength", 1)),
                                    "correct_answer_index", Map.of(
                                        "type", "integer", "minimum", 0, "maximum", 5))))))));

        return Game.builder()
            .id(19L)
            .title("Trivia Quiz")
            .url("trivia-quiz")
            .configDefinitions(new ArrayList<>(List.of(
                GameConfigDefinition.builder().version(1L).jsonSchema(schema).build())))
            .build();
    }

    /** Un juego que todavía no pide contenido al anunciante. */
    private static Game tapToRotate() {
        return Game.builder()
            .id(1L)
            .title("Tap To Rotate")
            .url("tap-to-rotate")
            .configDefinitions(new ArrayList<>(List.of(
                GameConfigDefinition.builder().version(1L).jsonSchema(Map.of()).build())))
            .build();
    }

    private static Map<String, Object> questions(int count) {
        List<Map<String, Object>> list = IntStream.rangeClosed(1, count)
            .mapToObj(i -> Map.<String, Object>of(
                "id", i,
                "question", "¿Pregunta " + i + " sobre la marca?",
                "options", List.of("Opción A", "Opción B", "Opción C", "Opción D"),
                "correct_answer_index", 0))
            .toList();

        return Map.of("game", Map.of("questions", list));
    }

    @Test
    @DisplayName("diez preguntas completas pasan")
    void tenQuestionsPass() {
        assertThatCode(() -> validator.validateOrThrow(triviaQuiz(), questions(10)))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("nueve preguntas no alcanzan, aunque el esquema sembrado diga minItems 1")
    void nineQuestionsFail() {
        // Este es el caso que hoy llega al juego: el schema del seed acepta una sola
        // pregunta y la trivia se entrega jugable a medias.
        assertThatThrownBy(() -> validator.validateOrThrow(triviaQuiz(), questions(9)))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("sin contenido, el error dice cuántas preguntas hacen falta")
    void emptyBriefNamesTheAmount() {
        // "Falta el contenido" no le sirve al anunciante: necesita el número.
        assertThatThrownBy(() -> validator.validateOrThrow(triviaQuiz(), Map.of()))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("10")
            .hasMessageContaining("preguntas");
    }

    @Test
    @DisplayName("una pregunta sin opciones no pasa: el recorte arrastra las reglas del item")
    void questionWithoutOptionsFails() {
        Map<String, Object> incompleto = Map.of("game", Map.of("questions",
            IntStream.rangeClosed(1, 10)
                .mapToObj(i -> Map.<String, Object>of("id", i, "question", "¿Pregunta " + i + "?"))
                .toList()));

        assertThatThrownBy(() -> validator.validateOrThrow(triviaQuiz(), incompleto))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("treinta preguntas, el tope de trivia, pasan")
    void thirtyQuestionsPass() {
        assertThatCode(() -> validator.validateOrThrow(triviaQuiz(), questions(30)))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("treinta y una no: el brief tiene tope")
    void thirtyOneQuestionsFail() {
        // Sin maxItems un solo PATCH guardaba miles de preguntas en brief_data, y de
        // ahí se copiaban al borrador del diseñador.
        assertThatThrownBy(() -> validator.validateOrThrow(triviaQuiz(), questions(31)))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("una clave extra en la raíz del brief se rechaza")
    void extraRootKeyFails() {
        Map<String, Object> conExtra = new java.util.HashMap<>(questions(10));
        conExtra.put("branding", Map.of("main_logo_url", "https://otro-sitio/logo.png"));

        assertThatThrownBy(() -> validator.validateOrThrow(triviaQuiz(), conExtra))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("una clave extra junto a las preguntas se rechaza")
    void extraKeyNextToQuestionsFails() {
        @SuppressWarnings("unchecked")
        Map<String, Object> game = new java.util.HashMap<>(
            (Map<String, Object>) questions(10).get("game"));
        game.put("relleno", "x".repeat(10_000));

        assertThatThrownBy(() -> validator.validateOrThrow(triviaQuiz(), Map.of("game", game)))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("un juego que no pide contenido no bloquea nada")
    void gameWithoutBriefIsNeverBlocked() {
        assertThatCode(() -> validator.validateOrThrow(tapToRotate(), null))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("y tampoco expone un esquema de formulario")
    void gameWithoutBriefHasNoSchema() {
        assertThatCode(() -> validator.briefJsonSchema(tapToRotate())).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(validator.briefJsonSchema(tapToRotate())).isNull();
    }
}
