package com.verygana2.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista ordenada de los {@code .sql} de referencia de {@code db/seed} (categorías, features,
 * pricing, legales, avatares, ubicaciones, categorías de producto, mascotas y juegos).
 *
 * <p>La comparten {@code DataSeeder} (dev) y {@code LoadTestSeeder} (prueba de carga) para no
 * tener dos listas vivas. Todos los scripts son idempotentes: {@code DataSeeder} los corre en
 * cada arranque de dev. No incluye los usuarios de prueba ({@code db/seed/test/}).
 */
public final class ReferenceSeedScripts {

    /** Datos base. El orden importa: primero las tablas sin dependencias externas. */
    public static final List<String> BASE_DATA = List.of(
            "db/seed/categories.sql",
            "db/seed/system-features.sql",
            "db/seed/pricing-config.sql",
            "db/seed/legal-documents.sql",
            "db/seed/avatars.sql",
            "db/seed/departments.sql",
            "db/seed/municipalities.sql", // depende de departamentos
            "db/seed/productCategories.sql",
            // Mascotas. Viven en beta/ porque también los va a cargar el seeder de beta (SCRUM-83).
            "db/seed/beta/pet-catalog-baked.sql",
            "db/seed/beta/pet-scenes.sql",
            // En dev no inserta nada: el comercial que busca solo existe en beta.
            "db/seed/beta/pet-commercial-items.sql");

    /** Juegos de Cali y Bogotá, con sus versiones v2 (el orden de cada v2 va tras su v1). */
    public static final List<String> GAMES = List.of(
            "db/seed/games/cali/avoid-the-bomb.sql",
            "db/seed/games/cali/ball-bounce.sql",
            // v2: agrega game_config.initial_lives, que el build lee y la v1 no declaraba.
            "db/seed/games/cali/ball-bounce-v2.sql",
            "db/seed/games/cali/balloon-lift.sql",
            "db/seed/games/cali/catch-it.sql",
            "db/seed/games/cali/hangman.sql",
            // v2: contenido en español y sin el default de palabras en inglés.
            "db/seed/games/cali/hangman-v2.sql",
            "db/seed/games/cali/match3.sql",
            "db/seed/games/cali/memory.sql",
            "db/seed/games/cali/sudoku.sql",
            "db/seed/games/cali/tap-to-rotate.sql",
            "db/seed/games/cali/whack-a-mole.sql",
            "db/seed/games/bogota/dash-runner.sql",
            "db/seed/games/bogota/endless-runner.sql",
            // v2: exige keys_per_action >= 1 y key_spawn_probability > 0; con 0 el build descarta la config.
            "db/seed/games/bogota/endless-runner-v2.sql",
            "db/seed/games/bogota/memory-match.sql",
            "db/seed/games/bogota/mini-flappy.sql",
            "db/seed/games/bogota/simple-crossword.sql",
            // v2: el bloque de contenido en español, que ahora lo llena el anunciante.
            "db/seed/games/bogota/simple-crossword-v2.sql",
            "db/seed/games/bogota/stack-tower.sql",
            "db/seed/games/bogota/tic-tac-toe.sql",
            "db/seed/games/bogota/tile-puzzle.sql",
            "db/seed/games/bogota/trivia-quiz.sql",
            // v2: el bloque de contenido en español y las opciones acotadas a 4.
            "db/seed/games/bogota/trivia-quiz-v2.sql",
            "db/seed/games/bogota/word-search.sql",
            // v2: el bloque de contenido en español y palabras de máximo 10 letras.
            "db/seed/games/bogota/word-search-v2.sql");

    private ReferenceSeedScripts() {
    }

    /** Datos base y juegos, en el orden en que {@code DataSeeder} los carga. */
    public static List<String> all() {
        List<String> all = new ArrayList<>(BASE_DATA);
        all.addAll(GAMES);
        return List.copyOf(all);
    }
}
