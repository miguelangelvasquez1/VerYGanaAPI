package com.verygana2.controllers.gameAssetsBorrar.cali;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class TapToRotateAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "default"));

        // game_config
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("scroll_speed", 1.0);
        gameConfig.put("max_scroll_speed", 1.0);
        gameConfig.put("acceleration", 0.0);
        gameConfig.put("jump_force", 12.0);
        gameConfig.put("gravity_scale", 3.0);
        gameConfig.put("game_duration", 30);
        gameConfig.put("use_countdown", true);
        gameConfig.put("max_lives", 3);
        root.set("game_config", gameConfig);

        // branding
        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_logo_url", "https://placehold.co/400x200/FF5733/FFFFFF.png?text=LOGO");
        images.put("main_logo_offset_y", 50.0);
        images.put("logo_watermark_url", "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK");
        images.put("logo_watermark_offset_y", -50.0);
        branding.set("images", images);

        ObjectNode visuals = MAPPER.createObjectNode();
        visuals.put("player_url", "https://placehold.co/128x128/00FF00/000000.png?text=PLAYER");
        visuals.put("ground_url", "https://placehold.co/128x128/654321/FFFFFF.png?text=GROUND");
        visuals.put("ground_trap_url", "https://placehold.co/128x128/FF0000/FFFFFF.png?text=TRAP");
        visuals.put("coin_url", "https://placehold.co/128x128/FFFF00/000000.png?text=COIN");
        visuals.put("air_trap_url", "https://placehold.co/128x128/FF00FF/FFFFFF.png?text=MINE");
        visuals.put("bg_image_url", "https://placehold.co/1024x512/000033/FFFFFF.png?text=BG");
        visuals.put("death_wall_url", "https://placehold.co/512x512/660000/FFFFFF.png?text=DEATH");
        visuals.put("coin_scale", 1.2);
        visuals.put("air_trap_scale", 1.0);
        visuals.put("ground_trap_scale", 0.9);
        visuals.put("ground_texture_scale", 1.0);
        visuals.put("bg_texture_scale", 1.0);
        visuals.put("bg_solid_color", "#1a0b2e");
        visuals.put("ground_color", "#ffffff");
        visuals.put("dw_primary_color", "#ff0000");
        visuals.put("dw_secondary_color", "#550000");
        visuals.put("dw_bg_color", "#220000");
        visuals.put("dw_scroll_x", 0.5);
        visuals.put("dw_scroll_y", 0.5);
        visuals.put("dw_desphase_x", 0.1);
        visuals.put("dw_desphase_y", 0.1);
        branding.set("visuals", visuals);

        ObjectNode bgConfig = MAPPER.createObjectNode();
        ObjectNode front = MAPPER.createObjectNode();
        front.put("SpriteUrl", "https://placehold.co/512x512/FFFFFF/000000.png?text=PATTERN");
        front.put("ColorHex", "#FFFFFF80"); front.put("Enabled", true); front.put("Speed", 0.1);
        front.put("Rotation", 0.0); front.put("LayoutMode", "TiledSquare"); front.put("AspectRatio", 1.0);
        bgConfig.set("Front", front);
        ObjectNode back = MAPPER.createObjectNode();
        back.put("SpriteUrl", ""); back.put("ColorHex", "#0A0A1E"); back.put("Enabled", true); back.put("Speed", 0.0);
        back.put("Rotation", 0.0); back.put("LayoutMode", "Stretched"); back.put("AspectRatio", 1.77);
        bgConfig.set("Back", back);
        branding.set("background_config", bgConfig);
        root.set("branding", branding);

        // audio
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://games.verygana.com/asset_tests/music-guitar.wav");
        audio.put("jump_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("land_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("coin_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("death_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("victory_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("lose_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

        // texts
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡MISIÓN CUMPLIDA!");
        texts.put("victory_phrase", "Excelente trabajo, sobreviviste.");
        texts.put("defeat_title", "¡CUIDADO AHI!");
        texts.put("defeat_phrase", "Mejor suerte la próxima vez.");
        texts.put("label_score", "LLAVES");
        texts.put("label_time", "TIEMPO");
        texts.put("label_record", "RÉCORD");
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

        ASSETS = root;
    }

    private TapToRotateAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}