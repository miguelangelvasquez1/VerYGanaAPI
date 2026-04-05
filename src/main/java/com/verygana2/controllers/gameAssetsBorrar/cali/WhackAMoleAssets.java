package com.verygana2.controllers.gameAssetsBorrar.cali;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class WhackAMoleAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "default"));

        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_image_url", "https://games.verygana.com/asset_tests/redbull/redbull-logo.png");
        // images.put("logo_watermark_url", "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK");
        branding.set("images", images);

        ObjectNode shaderBg = MAPPER.createObjectNode();
        ObjectNode front = MAPPER.createObjectNode();
        front.put("Enabled", false);
        front.put("SpriteUrl", "");
        shaderBg.set("Front", front);
        ObjectNode back = MAPPER.createObjectNode();
        back.put("Alpha", 1.0);
        back.put("ColorHex", "#FFFFFF");
        back.put("SpriteUrl", "https://games.verygana.com/asset_tests/redbull/fondo/azul.jpg");
        shaderBg.set("Back", back);
        branding.set("shader_background_config", shaderBg);
        root.set("branding", branding);

        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡EXCELENTE!");
        texts.put("victory_phrase", "¡Has machacado a todos los topos!");
        texts.put("defeat_title", "INTÉNTALO DE NUEVO");
        texts.put("defeat_phrase", "¡No te rindas, sigue practicando!");
        root.set("texts", texts);

        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("duration", 60.0);
        gameConfig.put("gridRows", 3);
        gameConfig.put("gridCols", 2);
        gameConfig.put("audioDivisions", 6);
        gameConfig.put("spawnInterval", 0.8);
        gameConfig.put("moleLifetime", 2.0);
        gameConfig.put("pointsPerHit", 10);
        gameConfig.put("maxLives", 3);
        root.set("game_config", gameConfig);

        // ObjectNode personalization = MAPPER.createObjectNode();
        // personalization.put("coin_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COIN");
        // personalization.put("coin_count_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COUNT");
        // root.set("personalization", personalization);

        ObjectNode game = MAPPER.createObjectNode();
        game.put("holeSpriteUrl", "https://games.verygana.com/asset_tests/redbull/redbull-latacerrada.png");
        game.put("moleSpriteUrl", "https://games.verygana.com/asset_tests/redbull/redbull-lataabierta.png");
        game.put("hitSpriteUrl", "https://games.verygana.com/asset_tests/redbull/redbull-lataabierta.png");
        game.put("errorSpriteUrl", "https://games.verygana.com/asset_tests/redbull/redbull-latarota.png");
        game.put("holeColorHex", "#FFFFFF");
        game.put("moleColorHex", "#FFFFFF");
        game.put("hitColorHex", "#FFFFFF");
        game.put("errorColorHex", "#FFFFFF");
        game.put("showPreviewButtons", true);
        game.put("previewButtonSpriteUrl", "https://games.verygana.com/asset_tests/redbull/redbbull-playbutton.png");
        game.put("previewButtonColorHex", "#FFFFFF");
        root.set("game", game);

        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://games.verygana.com/asset_tests/redbull/redbull-tedaalas.mp3");
        audio.put("main_audio_url", "https://games.verygana.com/asset_tests/redbull/redbull-tedaalas.mp3");
        audio.put("hit_sfx_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("miss_sfx_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("win_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("lose_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("coins_per_action", 20); //por cada boton golpeado
        rewards.put("coins_on_completion", 200);
        root.set("rewards", rewards);

        ASSETS = root;
    }

    private WhackAMoleAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}