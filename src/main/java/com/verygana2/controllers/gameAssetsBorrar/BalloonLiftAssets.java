package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Configuración completa del juego Balloon Lift (Globo Aerostático)
 */
public final class BalloonLiftAssets {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();

        // META
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "BALLOON_LIFT_DEFAULT"));

        // GAME_CONFIG
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("time_limit", 120);
        gameConfig.put("difficulty", "normal");
        gameConfig.put("max_attempts", 3);
        root.set("game_config", gameConfig);

        // BRANDING
        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_logo_url", "https://cdn-icons-png.flaticon.com/512/2917/2917176.png");
        images.put("main_logo_offset_y", 50);
        images.put("logo_watermark_url", "https://cdn-icons-png.flaticon.com/512/616/616554.png");
        images.put("logo_watermark_offset_y", 0);
        branding.set("images", images);

        // Shader Background Config
        ObjectNode shaderBg = MAPPER.createObjectNode();
        shaderBg.set("Back", createShaderLayer(
            "https://img.freepik.com/free-vector/blue-sky-white-clouds-background_1308-47404.jpg",
            true, "#FFFFFFFF", 0.05, 1.0
        ));
        shaderBg.set("Front", createShaderLayer("", false, "#FFFFFF00", 0.1, 0.3));
        branding.set("shader_background_config", shaderBg);

        root.set("branding", branding);

        // GAME
        ObjectNode game = MAPPER.createObjectNode();
        ObjectNode gameImages = MAPPER.createObjectNode();
        gameImages.put("balloon_sprite_url", "https://cdn-icons-png.flaticon.com/512/2917/2917176.png");
        game.set("images", gameImages);

        game.set("stages", MAPPER.createArrayNode());
        
        ArrayNode obstacles = MAPPER.createArrayNode();
        obstacles.add("https://cdn-icons-png.flaticon.com/512/562/562678.png");
        obstacles.add("https://cdn-icons-png.flaticon.com/512/2797/2797387.png");
        game.set("obstacle_models_urls", obstacles);

        game.put("coin_model_url", "https://cdn-icons-png.flaticon.com/512/3022/3022433.png");
        game.set("power_ups_config", MAPPER.createArrayNode());

        ObjectNode attributes = MAPPER.createObjectNode();
        attributes.put("lift_force", 4.0);
        attributes.put("horizontal_force", 10.0);
        attributes.put("max_velocity", 8.0);
        attributes.put("level_duration", 120.0);
        attributes.put("gravity", 1.0);
        attributes.put("obstacle_spawn_interval", 1.5);
        attributes.put("obstacle_speed", 4.0);
        attributes.put("sway_frequency", 1.5);
        attributes.put("sway_magnitude", 0.5);
        game.set("attributes", attributes);

        root.set("game", game);

        // AUDIO
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://cdn.pixabay.com/download/audio/2022/03/10/audio_c8c8e1c4c0.mp3");
        audio.put("victory_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_0d0e1b1d9e.mp3");
        audio.put("lose_url", "https://cdn.pixabay.com/download/audio/2022/03/15/audio_688cfb3a52.mp3");
        audio.put("coin_url", "https://cdn.pixabay.com/download/audio/2022/03/10/audio_c8c8e1c4c0.mp3");
        audio.put("obstacle_url", "https://cdn.pixabay.com/download/audio/2022/03/24/audio_ce0e1c5fb5.mp3");
        audio.put("powerup_url", "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3");
        audio.put("shield_url", "https://cdn.pixabay.com/download/audio/2021/08/09/audio_bb630cc098.mp3");
        root.set("audio", audio);

        // TEXTS
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡VICTORIA!");
        texts.put("victory_phrase", "¡Vuelo perfecto!");
        texts.put("defeat_title", "DERROTA");
        texts.put("defeat_phrase", "¡Inténtalo de nuevo!");
        root.set("texts", texts);

        // REWARDS
        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("coins_per_action", 1);
        rewards.put("coins_on_completion", 30);
        root.set("rewards", rewards);

        ASSETS = root;
    }

    private static ObjectNode createShaderLayer(String url, boolean enabled, String color, double speed, double alpha) {
        ObjectNode layer = MAPPER.createObjectNode();
        layer.put("SpriteUrl", url);
        layer.put("Enabled", enabled);
        layer.put("ColorHex", color);
        layer.put("Speed", speed);
        layer.put("Alpha", alpha);
        ObjectNode tiling = MAPPER.createObjectNode();
        tiling.put("x", 1);
        tiling.put("y", 1);
        layer.set("Tiling", tiling);
        ObjectNode direction = MAPPER.createObjectNode();
        direction.put("x", 1);
        direction.put("y", 0);
        layer.set("Direction", direction);
        layer.put("Rotation", 0);
        return layer;
    }

    private BalloonLiftAssets() {}

    public static ObjectNode getAssets() { return ASSETS; }

    public static String getAssetsAsString() {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) {
            return ASSETS.toString();
        }
    }
}