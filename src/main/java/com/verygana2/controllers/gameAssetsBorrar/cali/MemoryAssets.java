package com.verygana2.controllers.gameAssetsBorrar.cali;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class MemoryAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "default"));

        // game_config
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("start_pairs", 2);
        gameConfig.put("final_pairs", 10);
        gameConfig.put("game_duration", 120);
        gameConfig.put("bonus_per_pair", 5);
        gameConfig.put("preview_duration", 2);
        gameConfig.put("enable_lives", true);
        gameConfig.put("max_lives", 4);
        gameConfig.put("enable_powerups", true);
        gameConfig.put("power_up_chance", 0.3);
        gameConfig.put("bonus_time_amount", 15);

        ArrayNode levels = MAPPER.createArrayNode();
        double[][] levelData = {{1, 3, 0.3, 1.0, 1.0, 1.0}, {2, 6, 0.3, 1.0, 1.0, 1.0}, {3, 10, 0.2, 1.0, 1.5, 0.5}};
        String[] levelNames = {"Nivel 1", "Nivel 2", "Nivel 3"};
        for (int i = 0; i < 2; i++) {
            ObjectNode lvl = MAPPER.createObjectNode();
            lvl.put("id", (int) levelData[i][0]);
            lvl.put("stage_name", levelNames[i]);
            lvl.put("pair_count", (int) levelData[i][1]);
            lvl.put("power_up_chance", levelData[i][2]);
            lvl.put("weight_life", levelData[i][3]);
            lvl.put("weight_time", levelData[i][4]);
            lvl.put("weight_hint", levelData[i][5]);
            levels.add(lvl);
        }
        gameConfig.set("levels", levels);
        root.set("game_config", gameConfig);

        // branding
        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_logo_url", "https://games.verygana.com/asset_tests/fritolay/fritolay-logo.png");
        images.put("main_logo_offset_y", 0.0);
        // images.put("logo_watermark_url", "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK");
        images.put("logo_watermark_offset_y", 0.0);
        images.put("card_back_url", "https://placehold.co/256x256/3498db/ffffff.png?text=?");
        images.put("icon_life_url", "https://placehold.co/64x64/FF0000/FFFFFF.png?text=LIFE");
        images.put("icon_hint_url", "https://placehold.co/64x64/FFFF00/000000.png?text=HINT");
        images.put("icon_time_url", "https://placehold.co/64x64/00FF00/FFFFFF.png?text=TIME");
        branding.set("images", images);

        ObjectNode bgConfig = MAPPER.createObjectNode();
        ObjectNode front = MAPPER.createObjectNode();
        front.put("SpriteUrl", "https://games.verygana.com/asset_tests/fritolay/fritolay-fondo.jpeg"); front.put("ColorHex", "#FFFFFF"); front.put("Enabled", true);
        front.put("Speed", 0.2); front.put("Rotation", 0.0); front.put("LayoutMode", "TiledSquare"); front.put("AspectRatio", 1.0);
        bgConfig.set("Front", front);
        ObjectNode back = MAPPER.createObjectNode();
        back.put("SpriteUrl", "https://games.verygana.com/asset_tests/fritolay/fritolay-fondo.jpeg");
        back.put("ColorHex", ""); back.put("Enabled", false); back.put("Speed", 0.05);
        back.put("Rotation", 0.0); back.put("LayoutMode", "TiledSquare"); back.put("AspectRatio", 1.77);
        bgConfig.set("Back", back);
        branding.set("background_config", bgConfig);
        root.set("branding", branding);

        // audio
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://games.verygana.com/asset_tests/music-guitar.wav");
        audio.put("flip_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("success_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("error_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("powerup_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("victory_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("lose_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

        // texts
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡EXCELENTE!");
        texts.put("victory_phrase", "Has completado todos los niveles.");
        texts.put("defeat_title", "GAME OVER");
        texts.put("defeat_phrase", "¡Inténtalo de nuevo!");
        texts.put("label_time", "Tiempo");
        texts.put("label_attempts", "Intentos");
        texts.put("label_score", "Llaves");
        texts.put("label_level", "Nivel");
        root.set("texts", texts);

        // rewards
        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("coins_per_action", 20);
        rewards.put("coins_on_completion", 200);
        root.set("rewards", rewards);

        // personalization
        ObjectNode personalization = MAPPER.createObjectNode();
        personalization.put("coin_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COIN");
        personalization.put("coin_count_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COUNT");
        root.set("personalization", personalization);

        // game
        ObjectNode game = MAPPER.createObjectNode();
        ArrayNode cardImages = MAPPER.createArrayNode();
        String[] cardColors = {"margarita-negras-fondo.jpg", "cheesetris-fondo.jpg", "cheetos-fondo.jpg", "choclitos-fondo.jpg", "choclitos-picante-fondo.jpg", "detodito-bbq-fondo.jpg", "detodito-fondo.jpg", "doritos-flamin-fondo.jpg", "doritos-fondo.jpg", "margarita-fondo.jpg"};
        for (int i = 0; i < cardColors.length; i++) {
            ObjectNode card = MAPPER.createObjectNode();
            card.put("url", "https://games.verygana.com/asset_tests/fritolay/" + cardColors[i]);
            cardImages.add(card);
        }
        game.set("card_images", cardImages);
        root.set("game", game);

        ASSETS = root;
    }

    private MemoryAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}