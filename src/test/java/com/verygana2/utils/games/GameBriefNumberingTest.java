package com.verygana2.utils.games;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.utils.games.GameBriefCatalog.BriefField;

/**
 * Los ids de las preguntas los pone el backend, no el anunciante.
 *
 * El esquema exige un {@code id} por pregunta y el build lo lee, pero es un número
 * interno: pedírselo a la marca era hacerle llevar un contador a mano. Insertar una
 * pregunta en el medio bastaba para repetir un número —cosa que el esquema acepta,
 * porque solo pide {@code minimum: 1}— y el problema aparecía dentro del juego.
 */
class GameBriefNumberingTest {

    private final GameBriefNumbering numbering = new GameBriefNumbering();

    private static final List<BriefField> TRIVIA =
        List.of(new BriefField("game.questions", 10, 50, "preguntas", "id"));

    private static Map<String, Object> brief(Map<String, Object>... questions) {
        return Map.of("game", Map.of("questions", List.of(questions)));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> questionsOf(Map<String, Object> result) {
        return (List<Map<String, Object>>)
            ((Map<String, Object>) result.get("game")).get("questions");
    }

    @Test
    @DisplayName("numera por posición, desde 1")
    @SuppressWarnings("unchecked")
    void numbersByPosition() {
        Map<String, Object> out = numbering.apply(TRIVIA, brief(
            Map.of("question", "¿Una?"), Map.of("question", "¿Dos?"), Map.of("question", "¿Tres?")));

        assertThat(questionsOf(out)).extracting(q -> q.get("id")).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("pisa los números repetidos que haya escrito alguien")
    @SuppressWarnings("unchecked")
    void overwritesDuplicates() {
        // Tres preguntas con id 1: el esquema lo acepta —solo pide minimum 1— y el
        // juego se queda sin poder distinguirlas.
        Map<String, Object> out = numbering.apply(TRIVIA, brief(
            Map.of("id", 1, "question", "¿Una?"),
            Map.of("id", 1, "question", "¿Dos?"),
            Map.of("id", 1, "question", "¿Tres?")));

        assertThat(questionsOf(out)).extracting(q -> q.get("id")).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("no toca el resto de la pregunta")
    @SuppressWarnings("unchecked")
    void keepsTheRest() {
        Map<String, Object> out = numbering.apply(TRIVIA, brief(
            Map.of("question", "¿Cuál?", "options", List.of("A", "B"), "correct_answer_index", 1)));

        assertThat(questionsOf(out).get(0))
            .containsEntry("question", "¿Cuál?")
            .containsEntry("correct_answer_index", 1)
            .containsEntry("options", List.of("A", "B"));
    }

    @Test
    @DisplayName("no muta lo que recibe")
    void doesNotMutateInput() {
        // Llega desde el request y se guarda en la entidad: escribir encima del mapa
        // original haría que el contenido sin numerar dejara de existir para comparar.
        Map<String, Object> original = brief(Map.of("question", "¿Una?"));

        numbering.apply(TRIVIA, original);

        assertThat(questionsOf(original).get(0)).doesNotContainKey("id");
    }

    @Test
    @DisplayName("un juego sin identificador no se toca")
    void gamesWithoutAutoNumberAreUntouched() {
        List<BriefField> palabras = List.of(new BriefField("game.words", 10, 50, "palabras"));
        Map<String, Object> original = Map.of("game", Map.of("words", List.of(Map.of("word", "LLAVE"))));

        assertThat(numbering.apply(palabras, original)).isEqualTo(original);
    }

    @Test
    @DisplayName("un contenido vacío o sin la rama no revienta")
    void toleratesMissingBranches() {
        assertThat(numbering.apply(TRIVIA, Map.of())).isEmpty();
        assertThat(numbering.apply(TRIVIA, null)).isNull();
        assertThat(numbering.apply(TRIVIA, new LinkedHashMap<>(Map.of("game", Map.of()))))
            .isEqualTo(Map.of("game", Map.of()));
    }
}
