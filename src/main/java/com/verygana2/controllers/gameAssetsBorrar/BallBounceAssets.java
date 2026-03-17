package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class BallBounceAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "BALL_BOUNCE_DEFAULT"));

        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("difficulty", "Hard");
        gameConfig.put("game_duration_sec", 90);
        gameConfig.put("score_per_hit", 25);
        root.set("game_config", gameConfig);

        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_image_url", "https://images.unsplash.com/photo-1640317455707-d83d8d2e938f?q=80&w=1287&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D");
        images.put("main_image_offset_y", 40);
        images.put("logo_watermark_url", "https://images.unsplash.com/photo-1662948348853-ae7de7300e5e?q=80&w=1528&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D");
        images.put("logo_watermark_offset_y", 10);

        branding.set("images", images);

        ObjectNode shaderBg = MAPPER.createObjectNode();
        shaderBg.put("PrimaryColorHex", "#001F5B"); // Azul oscuro Red Bull
        shaderBg.put("SecondaryColorHex", "#D00027"); // Rojo Red Bull
        shaderBg.put("ParticleColorHex", "#FFD700"); // Dorado energético
        shaderBg.put("Speed", 1.2);
        shaderBg.put("Difficulty", 1.5);
        shaderBg.put("UseShader", true);
        shaderBg.set("Front", MAPPER.createObjectNode().put("Enabled", false));
        shaderBg.set("Back", MAPPER.createObjectNode().put("SpriteUrl", "").put("Enabled", true));
        branding.set("shader_background_config", shaderBg);
        root.set("branding", branding);

        ObjectNode game = MAPPER.createObjectNode();
        game.put("ball_speed", 650.0);
        game.put("paddle_speed", 700.0);
        game.put("brick_rows", 6);
        game.put("brick_columns", 10);

        game.put("url_ball_texture",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7a/Red_circle.svg/1024px-Red_circle.svg.png");

        game.put("url_paddle_texture",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4f/Red_Bull.svg/512px-Red_Bull.svg.png");

        ArrayNode levels = MAPPER.createArrayNode();

        ObjectNode level1 = MAPPER.createObjectNode();
        level1.put("level_difficulty", "Easy");
        level1.put("ball_speed_override", 450.0);
        level1.put("timer_sec", 60);
        level1.put("brick_rows", 3);
        level1.put("brick_columns", 5);
        level1.put("url_image", "");

        ArrayNode powerUps = MAPPER.createArrayNode();
        powerUps.add(createPowerUp("MultiBall", 0.15, 10, "#FFD700FF", ""));
        powerUps.add(createPowerUp("SpeedBoost", 0.20, 8, "#D00027FF", ""));
        powerUps.add(createPowerUp("Shield", 0.08, 6, "#001F5BFF", ""));

        level1.set("power_ups", powerUps);
        levels.add(level1);
        game.set("levels", levels);
        game.set("power_ups", MAPPER.createArrayNode());
        root.set("game", game);

        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("hit_url",
                "https://cdn.pixabay.com/download/audio/2022/03/15/audio_688cfb3a52.mp3");
        audio.put("brick_url",
                "https://cdn.pixabay.com/download/audio/2022/03/24/audio_ce0e1c5fb5.mp3");
        audio.put("win_url",
                "https://cdn.pixabay.com/download/audio/2021/08/04/audio_0d0e1b1d9e.mp3");
        audio.put("lose_url",
                "https://cdn.pixabay.com/download/audio/2022/03/10/audio_c8c8e1c4c0.mp3");
        audio.put("music_url",
                "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3");
        audio.put("laser_url",
                "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3");
        audio.put("powerup_url",
                "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3");


        ObjectNode texts = MAPPER.createObjectNode();

        texts.put("victory_phrase",
                "¡Rompes los límites!\\nRed Bull te da alas.");
        texts.put("defeat_phrase",
                "Vuelve más fuerte.\\nEl próximo round es tuyo.");

        texts.put("victory_title", "¡ENERGÍA TOTAL!");
        texts.put("defeat_title", "SIGUE INTENTANDO");

        ArrayNode floatingWords = MAPPER.createArrayNode();
        floatingWords.add("¡POWER!");
        floatingWords.add("¡BOOST!");
        floatingWords.add("¡FLY!");
        floatingWords.add("¡FULL SPEED!");
        
        texts.set("floating_words", floatingWords);
        texts.put("floating_color_hex", "#00FFFF");
        texts.put("floating_font_size", 42);
        texts.put("show_particles_with_words", false);
        root.set("texts", texts);

        ASSETS = root;
    }

    private static ObjectNode createPowerUp(String type, double chance, int duration, String color, String icon) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("type", type);
        p.put("drop_chance", chance);
        p.put("duration", duration);
        p.put("color_hex", color);
        p.put("url_icon", icon);
        return p;
    }

    private BallBounceAssets() {
    }

    public static ObjectNode getAssets() {
        return ASSETS;
    }

    public static String getAssetsAsString() {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) {
            return ASSETS.toString();
        }
    }
}