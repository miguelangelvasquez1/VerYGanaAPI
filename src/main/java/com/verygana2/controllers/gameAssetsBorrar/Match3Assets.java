package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class Match3Assets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "default"));

        // game_config
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("time_limit", 60);
        gameConfig.put("difficulty", "normal");
        gameConfig.put("max_attempts", 3);
        gameConfig.put("target_tiles", 10);
        gameConfig.put("total_levels", 1);
        root.set("game_config", gameConfig);

        // branding
        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_logo_url", "https://placehold.co/400x200/FF5733/FFFFFF.png?text=LOGO");
        images.put("main_logo_offset_y", 0.0);
        images.put("logo_watermark_url", "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK");
        images.put("logo_watermark_offset_y", 0.0);
        images.put("background_url", "");
        images.put("background_color_hex", "#FFFFFF");
        images.put("grid_background_url", "https://placehold.co/400x400/222222/444444.png?text=Grid+BG");
        images.put("grid_background_color_hex", "#FFFFFF");
        branding.set("images", images);

        ObjectNode bgConfig = MAPPER.createObjectNode();
        ObjectNode front = MAPPER.createObjectNode();
        front.put("SpriteUrl", ""); front.put("ColorHex", "#FFFFFF"); front.put("Enabled", true);
        front.put("Speed", 0.2); front.put("Rotation", 0.0); front.put("LayoutMode", "TiledSquare"); front.put("AspectRatio", 1.0);
        bgConfig.set("Front", front);
        ObjectNode back = MAPPER.createObjectNode();
        back.put("SpriteUrl", "https://placehold.co/1024x512/000033/FFFFFF.png?text=BG");
        back.put("ColorHex", "#FFFFFF"); back.put("Enabled", true); back.put("Speed", 0.05);
        back.put("Rotation", 0.0); back.put("LayoutMode", "Stretched"); back.put("AspectRatio", 1.77);
        bgConfig.set("Back", back);
        branding.set("background_config", bgConfig);
        root.set("branding", branding);

        // audio
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://games.verygana.com/asset_tests/music-guitar.wav");
        audio.put("victory_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("lose_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("match_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("swap_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

        // texts
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡VICTORIA!");
        texts.put("victory_phrase", "¡Nivel Completado!");
        texts.put("defeat_title", "¡INTÉNTALO DE NUEVO!");
        texts.put("defeat_phrase", "Se acabó el tiempo.");
        root.set("texts", texts);

        // rewards
        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("coins_per_action", 20);
        rewards.put("coins_on_completion", 200);
        rewards.put("combo_multiplier", 0.0);
        root.set("rewards", rewards);

        // personalization
        ObjectNode personalization = MAPPER.createObjectNode();
        personalization.put("coin_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COIN");
        personalization.put("coin_count_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COUNT");
        root.set("personalization", personalization);

        // game
        ObjectNode game = MAPPER.createObjectNode();
        ArrayNode tiles = MAPPER.createArrayNode();
        String[][] tileData = {
            {"1", "https://placehold.co/128x128/FF0000/FFFFFF.png?text=1", "10"},
            {"2", "https://placehold.co/128x128/00FF00/FFFFFF.png?text=2", "10"},
            {"3", "https://placehold.co/128x128/0000FF/FFFFFF.png?text=3", "10"},
            {"4", "https://placehold.co/128x128/FFFF00/000000.png?text=4", "10"},
            {"5", "https://placehold.co/128x128/FF00FF/FFFFFF.png?text=5+Star", "15"},
            {"6", "https://placehold.co/128x128/00FFFF/000000.png?text=6+Star", "15"}
        };
        for (String[] t : tileData) {
            ObjectNode tile = MAPPER.createObjectNode();
            tile.put("id", Integer.parseInt(t[0]));
            tile.put("sprite_url", t[1]);
            tile.put("score", Integer.parseInt(t[2]));
            tiles.add(tile);
        }
        game.set("tiles", tiles);
        root.set("game", game);

        ASSETS = root;
    }

    private Match3Assets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}