package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Configuración completa del juego Match 3 (Tres en Raya / Candy Crush)
 */
public final class Match3Assets {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();

        // META
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "MATCH3_DEFAULT"));

        // GAME_CONFIG
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("time_limit", 180);
        gameConfig.put("difficulty", "normal");
        gameConfig.put("max_attempts", 3);
        gameConfig.put("target_tiles", 20);
        gameConfig.put("total_levels", 5);
        root.set("game_config", gameConfig);

        // BRANDING
        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_logo_url", "https://cdn-icons-png.flaticon.com/512/2917/2917995.png");
        images.put("main_logo_offset_y", 60.0);
        images.put("logo_watermark_url", "https://cdn-icons-png.flaticon.com/512/3050/3050526.png");
        images.put("logo_watermark_offset_y", 0.0);
        images.put("background_url", "https://img.freepik.com/free-vector/gradient-galaxy-background_23-2148983652.jpg");
        images.put("background_color_hex", "#8B4789");
        images.put("grid_background_url", "");
        images.put("grid_background_color_hex", "#9B59B6");
        branding.set("images", images);

        ObjectNode bgConfig = MAPPER.createObjectNode();
        bgConfig.set("Front", createLayer("", "#FFFFFF00", false, 1.0));
        bgConfig.set("Back", createLayer("", "#9B59B6FF", true, 0.2));
        branding.set("background_config", bgConfig);

        // Audio dentro de branding
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://cdn.pixabay.com/download/audio/2022/03/10/audio_c8c8e1c4c0.mp3");
        audio.put("victory_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_0d0e1b1d9e.mp3");
        audio.put("lose_url", "https://cdn.pixabay.com/download/audio/2022/03/15/audio_688cfb3a52.mp3");
        audio.put("coin_url", "https://cdn.pixabay.com/download/audio/2022/03/10/audio_c8c8e1c4c0.mp3");
        audio.put("obstacle_url", "https://cdn.pixabay.com/download/audio/2022/03/24/audio_ce0e1c5fb5.mp3");
        branding.set("audio", audio);

        // Texts dentro de branding
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡VICTORIA!");
        texts.put("victory_phrase", "¡Felicidades, completaste el nivel!");
        texts.put("defeat_title", "DERROTA");
        texts.put("defeat_phrase", "Se acabó el tiempo. Inténtalo de nuevo.");
        branding.set("texts", texts);

        root.set("branding", branding);

        // GAME - Tiles
        ObjectNode game = MAPPER.createObjectNode();
        ArrayNode tiles = MAPPER.createArrayNode();
        String[] tileUrls = {
            "https://cdn-icons-png.flaticon.com/512/3050/3050526.png",
            "https://cdn-icons-png.flaticon.com/512/2917/2917995.png",
            "https://cdn-icons-png.flaticon.com/512/3022/3022433.png",
            "https://cdn-icons-png.flaticon.com/512/833/833472.png",
            "https://cdn-icons-png.flaticon.com/512/2838/2838590.png",
            "https://cdn-icons-png.flaticon.com/512/2965/2965279.png"
        };
        for (int i = 0; i < tileUrls.length; i++) {
            ObjectNode tile = MAPPER.createObjectNode();
            tile.put("id", i + 1);
            tile.put("sprite_url", tileUrls[i]);
            tile.put("score", 10 + (i * 5));
            tiles.add(tile);
        }
        game.set("tiles", tiles);
        root.set("game", game);

        ASSETS = root;
    }

    private static ObjectNode createLayer(String url, String color, boolean enabled, double speed) {
        ObjectNode layer = MAPPER.createObjectNode();
        layer.put("SpriteUrl", url);
        layer.put("ColorHex", color);
        layer.put("Enabled", enabled);
        layer.put("Speed", speed);
        layer.put("Rotation", 0);
        layer.put("LayoutMode", "Stretched");
        layer.put("AspectRatio", 1.77);
        return layer;
    }

    private Match3Assets() {}

    public static ObjectNode getAssets() { return ASSETS; }

    public static String getAssetsAsString() {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) {
            return ASSETS.toString();
        }
    }
}