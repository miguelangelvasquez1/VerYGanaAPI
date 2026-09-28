package com.verygana2.utils.games;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.models.games.Game;

/**
 * El envoltorio {@code game_data} que espera el build de Ball Bounce.
 *
 * Sin él, el juego recibía la configuración completa, no lanzaba ningún error de
 * parseo, y cargaba su configuración por defecto: el diseñador veía su brandeo
 * ignorado sin nada en pantalla ni en el log que lo explicara.
 */
class GameResponseEnvelopeTest {

    private final GameResponseEnvelope envelope = new GameResponseEnvelope();

    /** El slug real del seed: en cali es el game_title de la URL, ya escapado. */
    private static Game ballBounce() {
        return Game.builder().id(2L).title("Ball Bounce").url("Ball%20Bounce").build();
    }

    private static Map<String, Object> assets() {
        Map<String, Object> assets = new LinkedHashMap<>();
        assets.put("meta", Map.of("brand_id", "coca-cola"));
        assets.put("game", Map.of("ball_speed", 500));
        assets.put("reward_popup", Map.of("popup_title", "Recompensas", "products", java.util.List.of()));
        return assets;
    }

    @Test
    @DisplayName("ball bounce recibe la config bajo game_data")
    @SuppressWarnings("unchecked")
    void wrapsBallBounce() {
        Map<String, Object> out = envelope.wrap(ballBounce(), assets());

        assertThat(out).containsKey("game_data");
        Map<String, Object> inner = (Map<String, Object>) out.get("game_data");
        assertThat(inner).containsKeys("meta", "game", "reward_popup");
    }

    @Test
    @DisplayName("el reward_popup se queda también en la raíz")
    void keepsRewardPopupAtRoot() {
        // Lo lee SnowAssetsService, el servicio común que hace la petición, y ese no
        // pasa por el wrapper del juego: si solo viajara adentro, la campaña quedaría
        // sin productos en el popup.
        Map<String, Object> out = envelope.wrap(ballBounce(), assets());

        assertThat(out).containsKey("reward_popup");
        assertThat(out.get("reward_popup")).isEqualTo(assets().get("reward_popup"));
    }

    @Test
    @DisplayName("la raíz no lleva nada más que el sobre y el reward_popup")
    void rootHasNothingElse() {
        // El wrapper del build tiene un solo campo; dejar los bloques sueltos arriba
        // solo sirve para que alguien crea que el juego los está leyendo.
        assertThat(envelope.wrap(ballBounce(), assets())).containsOnlyKeys("game_data", "reward_popup");
    }

    @Test
    @DisplayName("los demás juegos siguen recibiendo la config plana")
    void otherGamesAreUntouched() {
        // game_data aparece una sola vez en todo el binario de cali y no existe en el
        // de bogotá: envolver a los demás los rompería igual que estaba ball bounce.
        for (String slug : new String[] {"Hangman", "Memory", "trivia-quiz", "memory-match"}) {
            Map<String, Object> assets = assets();
            Game game = Game.builder().id(9L).title(slug).url(slug).build();

            assertThat(envelope.wrap(game, assets)).as("juego %s", slug).isSameAs(assets);
        }
    }

    @Test
    @DisplayName("un juego sin slug no revienta")
    void nullSlug() {
        Map<String, Object> assets = assets();
        assertThat(envelope.wrap(Game.builder().id(9L).build(), assets)).isSameAs(assets);
        assertThat(envelope.wrap(null, assets)).isSameAs(assets);
    }
}
