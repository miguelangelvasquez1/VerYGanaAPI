package com.verygana2.utils.games;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.verygana2.models.games.Game;

/**
 * Qué necesita cada juego del anunciante, separado en dos cosas que no viajan igual.
 *
 * <p><b>El texto lo escribe la marca.</b> Las preguntas de la trivia con su respuesta
 * correcta, las palabras de la sopa de letras, las pistas del crucigrama: el
 * diseñador no puede inventarlo. Eso va en el brief, se valida contra el esquema y
 * se siembra en el borrador del diseñador, que sigue pudiendo corregirlo antes de
 * entregar.
 *
 * <p><b>Las imágenes NO.</b> El anunciante las sube como recursos corporativos, que
 * son privados y con URL temporal a propósito: el diseñador es el auditor. Él
 * revisa que el contenido sea apropiado y tenga calidad suficiente, y recién
 * entonces lo publica como asset del juego. Dejar que el anunciante escribiera URLs
 * directamente en la configuración se saltaba ese control. Por eso acá las imágenes
 * se expresan como un <b>mínimo de archivos</b>, no como un campo del formulario.
 *
 * <p><b>De dónde salen los mínimos.</b> De las configuraciones con las que se
 * probaron los builds, el único contrato verificable sin el binario: 10 preguntas
 * de 4 opciones en la trivia, 10 palabras en la sopa de letras, 10 pares
 * palabra-pista en el crucigrama y exactamente 25 cartas en el memory match.
 */
@Component
public class GameBriefCatalog {

    /**
     * Un bloque de contenido escrito que se le pide al anunciante.
     *
     * @param path           ruta dentro del {@code json_schema}, con puntos ({@code game.questions})
     * @param minItems       cuántos elementos necesita el juego para ser jugable
     * @param maxItems       cuántos acepta como máximo. Ningún esquema sembrado declara
     *                       un tope, y sin él un solo PATCH puede guardar miles de
     *                       preguntas en {@code brief_data}, que después se copian al
     *                       borrador del diseñador y de ahí a la config del juego.
     * @param label          cómo nombrarlo en el error, en español y para un anunciante
     * @param autoNumberKey  campo identificador que pone el backend por posición, o
     *                       {@code null} si el juego no usa ninguno. Se esconde del
     *                       formulario: es contabilidad interna del juego, no algo
     *                       que el anunciante deba llevar a mano.
     */
    public record BriefField(String path, int minItems, int maxItems, String label, String autoNumberKey) {

        public BriefField {
            if (minItems < 1 || maxItems < minItems) {
                throw new IllegalArgumentException(
                    "Rango inválido para " + path + ": " + minItems + ".." + maxItems);
            }
        }

        public BriefField(String path, int minItems, int maxItems, String label) {
            this(path, minItems, maxItems, label, null);
        }
    }

    /**
     * Cuántos archivos tiene que aportar el anunciante para que el diseñador pueda
     * armar el juego. No son assets del juego todavía: son la materia prima que él
     * revisa y publica.
     */
    public record ResourceRequirement(int minFiles, String label) {}

    /**
     * Indexado por {@code Game.url}, el slug estable que también arma la URL del build.
     *
     * Los máximos no salen del build —no hay forma de leerlos sin el binario—: son
     * holgados respecto del mínimo y su trabajo es cortar el abuso, no afinar el
     * juego. La sopa de letras y el crucigrama van más bajos porque las palabras
     * tienen que caber en el tablero.
     */
    private static final Map<String, List<BriefField>> TEXT_BY_GAME_SLUG = Map.of(
        "trivia-quiz", List.of(
            new BriefField("game.questions", 10, 30,
                "preguntas con sus opciones y la respuesta correcta", "id")),
        "word-search", List.of(
            new BriefField("game.words", 10, 20, "palabras para buscar")),
        "simple-crossword", List.of(
            new BriefField("game.entries", 10, 20, "palabras con su pista")),
        "Hangman", List.of(
            new BriefField("game.words", 3, 30, "palabras con su pista"))
    );

    private static final Map<String, ResourceRequirement> RESOURCES_BY_GAME_SLUG = Map.of(
        "memory-match", new ResourceRequirement(25,
            "imágenes distintas para las cartas: el juego arma una pareja con cada una")
    );

    /** Vacío para los juegos que no piden texto: no bloquean nada. */
    public List<BriefField> fieldsFor(Game game) {
        if (game == null || game.getUrl() == null) return List.of();
        return TEXT_BY_GAME_SLUG.getOrDefault(game.getUrl(), List.of());
    }

    /** Cuántos archivos exige este juego, si exige alguno. */
    public Optional<ResourceRequirement> requiredResourcesFor(Game game) {
        if (game == null || game.getUrl() == null) return Optional.empty();
        return Optional.ofNullable(RESOURCES_BY_GAME_SLUG.get(game.getUrl()));
    }

    /**
     * Los slugs configurados, para que el test los cruce con los seeds. Uno mal
     * escrito desactiva la exigencia en silencio: nadie le pide el contenido al
     * anunciante y nadie se entera.
     */
    Set<String> configuredGameSlugs() {
        return Set.copyOf(
            java.util.stream.Stream.concat(
                TEXT_BY_GAME_SLUG.keySet().stream(),
                RESOURCES_BY_GAME_SLUG.keySet().stream()
            ).toList());
    }
}
