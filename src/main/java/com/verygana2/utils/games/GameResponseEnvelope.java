package com.verygana2.utils.games;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.verygana2.models.games.Game;

/**
 * Envuelve la configuración cuando el build del juego la espera anidada.
 *
 * Ball Bounce es el único que lo hace, y por eso su brandeo no se veía nunca: el
 * juego respondía {@code [BallBounceInitializer] Los datos del juego son nulos tras
 * el parseo. Cargando configuración por defecto...} con la respuesta completa y
 * correcta en el log, y sin ningún error de parseo — porque el JSON era válido, solo
 * que el campo que el build busca no estaba.
 *
 * <p><b>De dónde sale.</b> Leído del binario del build el 2026-09-22
 * ({@code builds/build-cali/build-04-08-2026/Build/Minijuegos.data}, metadata IL2CPP)
 * <ul>
 *   <li>{@code BallBounce.Architecture.BallBounceInitializer|ApiResponseWrapper} es un
 *   tipo anidado del initializer, con un único campo: {@code game_data}. El juego
 *   deserializa la respuesta en ese wrapper, así que todo lo que no esté bajo
 *   {@code game_data} no existe para él.</li>
 *
 *   <li>Ningún otro juego declara un wrapper: {@code game_data} aparece una sola vez
 *   en todo el binario de cali, y el build de bogotá no lo tiene. Por eso esto es un
 *   mapa por juego y no un cambio global — envolver la respuesta de los demás los
 *   rompería a todos.</li>
 *
 *   <li>{@code reward_popup} se queda además en la raíz: lo lee
 *   {@code SnowAssetsService} —el servicio común que hace la petición— por su cuenta,
 *   contra {@code GameCampaignData}, y no pasa por el wrapper del juego. Son dos
 *   lectores distintos de la misma respuesta.</li>
 * </ul>
 */
@Component
public class GameResponseEnvelope {

    /** El bloque que el servicio común lee de la raíz, aunque el juego venga envuelto. */
    private static final String SHARED_ROOT_BLOCK = "reward_popup";

    /**
     * Indexado por {@code Game.url}. En cali el slug es el {@code game_title} de la
     * URL, ya escapado ({@code Ball%20Bounce}).
     */
    private static final Map<String, String> ENVELOPE_KEY_BY_SLUG = Map.of(
        "Ball%20Bounce", "game_data"
    );

    /**
     * @return la misma configuración si el juego la lee plana, o envuelta bajo su
     *         clave si el build la espera anidada
     */
    public Map<String, Object> wrap(Game game, Map<String, Object> assets) {
        String key = game == null || game.getUrl() == null
            ? null
            : ENVELOPE_KEY_BY_SLUG.get(game.getUrl());

        if (key == null) return assets;

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put(key, assets);
        if (assets.containsKey(SHARED_ROOT_BLOCK)) {
            envelope.put(SHARED_ROOT_BLOCK, assets.get(SHARED_ROOT_BLOCK));
        }
        return envelope;
    }
}
