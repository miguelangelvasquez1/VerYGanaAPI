package com.verygana2.utils.games;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.utils.games.GameBriefCatalog.BriefField;

/**
 * El recorte del esquema para el formulario del anunciante.
 *
 * Los dos riesgos que cubren estos tests son los que rompen un formulario generado:
 * un {@code required} que nombra una clave que el recorte eliminó (el esquema
 * rechaza todo y el anunciante no ve por qué), y un {@code minItems} que se queda en
 * el del esquema original —1— y deja pasar una trivia de una pregunta.
 */
class BriefSchemaExtractorTest {

    private final BriefSchemaExtractor extractor = new BriefSchemaExtractor();

    /** Recorte del esquema real de trivia-quiz, con lo que rodea a las preguntas. */
    private static Map<String, Object> triviaSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("meta", "branding", "game", "audio"),
            "properties", Map.of(
                "meta", Map.of("type", "object", "properties", Map.of(
                    "brand_id", Map.of("type", "string"))),
                "branding", Map.of("type", "object", "properties", Map.of(
                    "main_logo_url", Map.of("type", "string"))),
                "game", Map.of(
                    "type", "object",
                    "required", List.of("questions"),
                    "properties", Map.of(
                        "questions", Map.of(
                            "type", "array",
                            "minItems", 1,
                            "items", Map.of(
                                "type", "object",
                                "required", List.of("question", "options"),
                                "properties", Map.of(
                                    "question", Map.of("type", "string", "maxLength", 512),
                                    "options", Map.of("type", "array", "minItems", 2)))))),
                "audio", Map.of("type", "object", "properties", Map.of(
                    "victory_url", Map.of("type", "string")))));
    }

    private static final List<BriefField> TRIVIA =
        List.of(new BriefField("game.questions", 10, 50, "preguntas"));

    @Nested
    @DisplayName("json schema")
    class JsonSchema {

        @Test
        @DisplayName("deja solo la rama del anunciante")
        @SuppressWarnings("unchecked")
        void keepsOnlyTheBriefBranch() {
            Map<String, Object> out = extractor.jsonSchema(triviaSchema(), TRIVIA);

            Map<String, Object> props = (Map<String, Object>) out.get("properties");
            assertThat(props).containsOnlyKeys("game");

            Map<String, Object> game = (Map<String, Object>) props.get("game");
            assertThat((Map<String, Object>) game.get("properties")).containsOnlyKeys("questions");
        }

        @Test
        @DisplayName("el required no nombra nada que se haya podado")
        @SuppressWarnings("unchecked")
        void requiredOnlyNamesSurvivors() {
            // El esquema original exige meta, branding y audio: si ese required viajara
            // tal cual, el brief del anunciante sería inválido siempre y el mensaje
            // hablaría de campos que él no llena.
            Map<String, Object> out = extractor.jsonSchema(triviaSchema(), TRIVIA);

            assertThat((List<String>) out.get("required")).containsExactly("game");

            Map<String, Object> game = (Map<String, Object>)
                ((Map<String, Object>) out.get("properties")).get("game");
            assertThat((List<String>) game.get("required")).containsExactly("questions");
        }

        @Test
        @DisplayName("pisa el minItems del esquema con el que el juego necesita")
        @SuppressWarnings("unchecked")
        void overridesMinItems() {
            Map<String, Object> out = extractor.jsonSchema(triviaSchema(), TRIVIA);

            Map<String, Object> questions = (Map<String, Object>)
                ((Map<String, Object>) ((Map<String, Object>)
                    ((Map<String, Object>) out.get("properties")).get("game")).get("properties")).get("questions");

            assertThat(questions).containsEntry("minItems", 10);
        }

        @Test
        @DisplayName("pone el maxItems del catálogo")
        @SuppressWarnings("unchecked")
        void setsMaxItems() {
            Map<String, Object> out = extractor.jsonSchema(triviaSchema(), TRIVIA);

            Map<String, Object> questions = (Map<String, Object>)
                ((Map<String, Object>) ((Map<String, Object>)
                    ((Map<String, Object>) out.get("properties")).get("game")).get("properties")).get("questions");

            assertThat(questions).containsEntry("maxItems", 50);
        }

        @Test
        @DisplayName("si el esquema del juego ya trae un tope más bajo, manda ese")
        @SuppressWarnings("unchecked")
        void keepsTheStricterMax() {
            Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("game", Map.of(
                    "type", "object",
                    "properties", Map.of("questions", Map.of(
                        "type", "array", "maxItems", 12,
                        "items", Map.of("type", "object"))))));

            Map<String, Object> out = extractor.jsonSchema(schema, TRIVIA);

            Map<String, Object> questions = (Map<String, Object>)
                ((Map<String, Object>) ((Map<String, Object>)
                    ((Map<String, Object>) out.get("properties")).get("game")).get("properties")).get("questions");

            assertThat(questions).containsEntry("maxItems", 12);
        }

        @Test
        @DisplayName("la raíz y los tramos intermedios no aceptan claves extra")
        @SuppressWarnings("unchecked")
        void closesBuiltNodes() {
            Map<String, Object> out = extractor.jsonSchema(triviaSchema(), TRIVIA);

            Map<String, Object> game = (Map<String, Object>)
                ((Map<String, Object>) out.get("properties")).get("game");

            assertThat(out).containsEntry("additionalProperties", false);
            assertThat(game).containsEntry("additionalProperties", false);
        }

        @Test
        @DisplayName("conserva la definición del item: tipos, límites y su propio required")
        @SuppressWarnings("unchecked")
        void keepsItemDefinition() {
            // Sin esto el anunciante podría mandar preguntas sin opciones: el recorte
            // tiene que arrastrar las reglas de adentro, no solo el nombre del campo.
            Map<String, Object> out = extractor.jsonSchema(triviaSchema(), TRIVIA);

            Map<String, Object> questions = (Map<String, Object>)
                ((Map<String, Object>) ((Map<String, Object>)
                    ((Map<String, Object>) out.get("properties")).get("game")).get("properties")).get("questions");
            Map<String, Object> items = (Map<String, Object>) questions.get("items");

            assertThat((List<String>) items.get("required")).contains("question", "options");
            assertThat((Map<String, Object>) items.get("properties")).containsKeys("question", "options");
        }

        @Test
        @DisplayName("el identificador que pone el backend no aparece en el formulario")
        @SuppressWarnings("unchecked")
        void hidesTheAutoNumberedField() {
            // Y sale también de required: un campo requerido que el formulario no
            // dibuja deja al anunciante sin forma de enviarlo.
            Map<String, Object> out = extractor.jsonSchema(triviaSchema(),
                List.of(new BriefField("game.questions", 10, 50, "preguntas", "question")));

            Map<String, Object> items = (Map<String, Object>) ((Map<String, Object>)
                ((Map<String, Object>) ((Map<String, Object>)
                    ((Map<String, Object>) out.get("properties")).get("game")).get("properties"))
                        .get("questions")).get("items");

            assertThat((Map<String, Object>) items.get("properties")).doesNotContainKey("question");
            assertThat((List<String>) items.get("required")).doesNotContain("question");
            assertThat((List<String>) items.get("required")).contains("options");
        }

        @Test
        @DisplayName("no muta el esquema del juego que recibe")
        void doesNotMutateTheSourceSchema() {
            // Map.of es inmutable: si el extractor escribiera en el esquema de origen
            // —por ejemplo creando un properties que falta— esto explota.
            Map<String, Object> source = triviaSchema();

            extractor.jsonSchema(source, List.of(new BriefField("game.questions.nope", 3, 50, "x")));
            extractor.jsonSchema(source, TRIVIA);
        }

        @Test
        @DisplayName("un campo que el esquema no declara no inventa una rama")
        @SuppressWarnings("unchecked")
        void unknownPathIsIgnored() {
            Map<String, Object> out = extractor.jsonSchema(triviaSchema(),
                List.of(new BriefField("game.no_existe", 5, 50, "cosas")));

            assertThat((Map<String, Object>) out.get("properties")).isEmpty();
        }
    }

    @Nested
    @DisplayName("ui schema")
    class UiSchema {

        private Map<String, Object> triviaUi() {
            return Map.of(
                "ui:order", List.of("meta", "game", "audio"),
                "meta", Map.of("ui:title", "Meta"),
                "game", Map.of(
                    "ui:title", "Game Content",
                    "ui:description", "Define las preguntas",
                    "questions", Map.of(
                        "ui:title", "Questions",
                        "items", Map.of(
                            "ui:order", List.of("id", "question", "options"),
                            "id", Map.of("ui:widget", "numberInput"),
                            "question", Map.of("ui:widget", "textInput")))),
                "audio", Map.of("ui:title", "Audio"));
        }

        @Test
        @DisplayName("el campo que pone el backend sale también del ui:order de los items")
        @SuppressWarnings("unchecked")
        void dropsAutoNumberedFromItemOrder() {
            // RJSF falla si el ui:order nombra una propiedad que el esquema ya no
            // declara: el formulario del anunciante no se dibujaría.
            Map<String, Object> out = extractor.uiSchema(triviaUi(),
                List.of(new BriefField("game.questions", 10, 50, "preguntas", "id")));

            Map<String, Object> items = (Map<String, Object>) ((Map<String, Object>)
                ((Map<String, Object>) out.get("game")).get("questions")).get("items");

            assertThat((List<String>) items.get("ui:order")).containsExactly("question", "options");
            assertThat(items).doesNotContainKey("id");
        }

        @Test
        @DisplayName("conserva el título del bloque pero no sus hermanos")
        @SuppressWarnings("unchecked")
        void keepsBlockTitleWithoutSiblings() {
            Map<String, Object> out = extractor.uiSchema(triviaUi(), TRIVIA);

            assertThat(out).containsOnlyKeys("game", "ui:order");
            Map<String, Object> game = (Map<String, Object>) out.get("game");
            assertThat(game).containsEntry("ui:title", "Game Content").containsKey("questions");
        }

        @Test
        @DisplayName("reescribe el ui:order con lo que quedó")
        @SuppressWarnings("unchecked")
        void rewritesOrder() {
            // El ui:order original nombra meta y audio: dejarlo deja huecos en el form.
            Map<String, Object> out = extractor.uiSchema(triviaUi(), TRIVIA);

            assertThat((List<String>) out.get("ui:order")).containsExactly("game");
            assertThat((List<String>) ((Map<String, Object>) out.get("game")).get("ui:order"))
                .containsExactly("questions");
        }

        @Test
        @DisplayName("no muta el ui_schema del juego que recibe")
        void doesNotMutateTheSource() {
            // Llega desde la entidad de Hibernate: escribirle encima ensuciaría la
            // definición del juego para todo el que la lea después.
            Map<String, Object> source = new java.util.LinkedHashMap<>(triviaUi());
            Map<String, Object> game = new java.util.LinkedHashMap<>((Map<String, Object>) source.get("game"));
            source.put("game", game);

            extractor.uiSchema(source, TRIVIA);

            assertThat(game).doesNotContainKey("ui:order");
        }

        @Test
        @DisplayName("sin ui_schema devuelve vacío en vez de reventar")
        void nullUiSchema() {
            assertThat(extractor.uiSchema(null, TRIVIA)).isEmpty();
        }
    }
}
