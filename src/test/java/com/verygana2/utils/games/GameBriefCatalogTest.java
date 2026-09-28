package com.verygana2.utils.games;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.models.games.Game;
import com.verygana2.utils.games.GameBriefCatalog.BriefField;

/**
 * Los mínimos de contenido y el enganche con los juegos sembrados.
 *
 * Las cantidades no son opinión: son las de las configuraciones con las que se
 * probaron los builds (10 preguntas en la trivia, 25 cartas en el memory match). Si
 * alguien las baja sin querer, el juego se entrega a medias y solo se nota jugando.
 */
class GameBriefCatalogTest {

    private final GameBriefCatalog catalog = new GameBriefCatalog();

    private static Game game(String slug) {
        return Game.builder().id(1L).title(slug).url(slug).build();
    }

    private BriefField onlyField(String slug) {
        List<BriefField> fields = catalog.fieldsFor(game(slug));
        assertThat(fields).hasSize(1);
        return fields.get(0);
    }

    @Test
    @DisplayName("trivia: 10 preguntas, no la que dice el esquema sembrado")
    void trivia() {
        assertThat(onlyField("trivia-quiz")).isEqualTo(new BriefField(
            "game.questions", 10, 30, "preguntas con sus opciones y la respuesta correcta", "id"));
    }

    @Test
    @DisplayName("solo trivia lleva identificador automático")
    void onlyTriviaAutoNumbers() {
        // El id es contabilidad interna del juego: el formulario no lo pide y el
        // backend lo escribe por posición. Los otros no tienen nada equivalente, y
        // marcarles un campo por error escondería algo que la marca sí debe llenar.
        assertThat(onlyField("trivia-quiz").autoNumberKey()).isEqualTo("id");
        assertThat(onlyField("word-search").autoNumberKey()).isNull();
        assertThat(onlyField("simple-crossword").autoNumberKey()).isNull();
    }

    @Test
    @DisplayName("memoria: 25 imágenes, pero como archivos que audita el diseñador")
    void memoryMatch() {
        // Las imágenes no son un campo del formulario del anunciante: él las sube como
        // recursos corporativos —privados y temporales— y el diseñador revisa que sean
        // apropiadas y estén en calidad antes de publicarlas como assets del juego.
        assertThat(catalog.fieldsFor(game("memory-match"))).isEmpty();

        var requirement = catalog.requiredResourcesFor(game("memory-match"));
        assertThat(requirement).isPresent();
        assertThat(requirement.get().minFiles()).isEqualTo(25);
    }

    @Test
    @DisplayName("los juegos de texto no exigen archivos")
    void textGamesNeedNoFiles() {
        assertThat(catalog.requiredResourcesFor(game("trivia-quiz"))).isEmpty();
        assertThat(catalog.requiredResourcesFor(game("word-search"))).isEmpty();
    }

    @Test
    @DisplayName("sopa de letras y crucigrama: 10 cada uno")
    void wordGames() {
        assertThat(onlyField("word-search").minItems()).isEqualTo(10);
        assertThat(onlyField("word-search").path()).isEqualTo("game.words");
        assertThat(onlyField("simple-crossword").minItems()).isEqualTo(10);
        assertThat(onlyField("simple-crossword").path()).isEqualTo("game.entries");
    }

    @Test
    @DisplayName("hangman: 3 palabras, y el slug va como lo nombra cali")
    void hangman() {
        // Los juegos de cali se indexan por el game_title de la URL, que va capitalizado
        // («Hangman», «Ball%20Bounce»). Con el slug en minúsculas el catálogo no
        // engancha y el paso de contenido no aparece, sin ningún error.
        assertThat(onlyField("Hangman").minItems()).isEqualTo(3);
        assertThat(onlyField("Hangman").path()).isEqualTo("game.words");
        assertThat(catalog.fieldsFor(game("hangman"))).isEmpty();
    }

    @Test
    @DisplayName("un juego fuera del catálogo no pide nada y no bloquea")
    void unknownGame() {
        assertThat(catalog.fieldsFor(game("tap-to-rotate"))).isEmpty();
        assertThat(catalog.requiredResourcesFor(game("tap-to-rotate"))).isEmpty();
        assertThat(catalog.fieldsFor(null)).isEmpty();
        assertThat(catalog.requiredResourcesFor(null)).isEmpty();
    }

    @Test
    @DisplayName("todo campo del catálogo tiene tope, y el tope no baja del mínimo")
    void everyFieldHasACap() {
        for (String slug : catalog.configuredGameSlugs()) {
            for (BriefField field : catalog.fieldsFor(game(slug))) {
                assertThat(field.maxItems())
                    .as("tope de %s en %s", field.path(), slug)
                    .isGreaterThanOrEqualTo(field.minItems());
            }
        }
    }

    @Test
    @DisplayName("un rango al revés no se puede ni construir")
    void invertedRangeIsRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new BriefField("game.words", 10, 5, "palabras"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("cada slug del catálogo existe entre los juegos sembrados")
    void slugsMatchSeededGames() throws IOException {
        // Un slug mal escrito no falla en ningún lado: simplemente nadie le pide el
        // contenido al anunciante y el diseñador vuelve a inventar las preguntas.
        for (String slug : catalog.configuredGameSlugs()) {
            assertThat(seedFor(slug))
                .as("seed del juego %s", slug)
                .isNotNull()
                .contains("'" + slug + "'");
        }
    }

    /** Los seeds viven en resources, así que el test los lee del classpath. */
    private String seedFor(String slug) throws IOException {
        for (String city : List.of("bogota", "cali")) {
            try (InputStream in = getClass().getClassLoader()
                    .getResourceAsStream("db/seed/games/" + city + "/" + slug + ".sql")) {
                if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
