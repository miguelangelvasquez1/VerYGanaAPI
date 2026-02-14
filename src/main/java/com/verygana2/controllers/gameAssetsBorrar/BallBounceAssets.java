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
        gameConfig.put("difficulty", "Medium");
        gameConfig.put("game_duration_sec", 60);
        gameConfig.put("score_per_hit", 10);
        root.set("game_config", gameConfig);

        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_image_url", "https://cdn-icons-png.flaticon.com/512/3069/3069172.png");
        images.put("main_image_offset_y", 50);
        images.put("logo_watermark_url", "https://cdn-icons-png.flaticon.com/512/2917/2917995.png");
        images.put("logo_watermark_offset_y", 0);
        branding.set("images", images);

        ObjectNode shaderBg = MAPPER.createObjectNode();
        shaderBg.put("PrimaryColorHex", "#1a0b2e");
        shaderBg.put("SecondaryColorHex", "#2d1b4e");
        shaderBg.put("ParticleColorHex", "#432c7a");
        shaderBg.put("Speed", 0.5);
        shaderBg.put("Difficulty", 1.0);
        shaderBg.put("UseShader", true);
        shaderBg.set("Front", MAPPER.createObjectNode().put("Enabled", false));
        shaderBg.set("Back", MAPPER.createObjectNode().put("SpriteUrl", "").put("Enabled", true));
        branding.set("shader_background_config", shaderBg);
        root.set("branding", branding);

        ObjectNode game = MAPPER.createObjectNode();
        game.put("ball_speed", 500.0);
        game.put("paddle_speed", 600.0);
        game.put("brick_rows", 5);
        game.put("brick_columns", 9);
        game.put("url_ball_texture", "https://www.pngall.com/wp-content/uploads/13/Red-Circle-PNG-Pic.png");
        game.put("url_paddle_texture", "https://static.vecteezy.com/system/resources/previews/024/094/088/non_2x/white-board-free-png.png");

        ArrayNode levels = MAPPER.createArrayNode();
        ObjectNode level1 = MAPPER.createObjectNode();
        level1.put("level_difficulty", "Easy");
        level1.put("ball_speed_override", 450.0);
        level1.put("timer_sec", 60);
        level1.put("brick_rows", 3);
        level1.put("brick_columns", 5);
        level1.put("url_image", "");
        ArrayNode powerUps = MAPPER.createArrayNode();
        powerUps.add(createPowerUp("MultiBall", 0.1, 0, "#FFFF00FF", ""));
        powerUps.add(createPowerUp("Life", 0.05, 0, "#FF0000FF", ""));
        level1.set("power_ups", powerUps);
        levels.add(level1);
        game.set("levels", levels);
        game.set("power_ups", MAPPER.createArrayNode());
        root.set("game", game);

        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("hit_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_12b0c7443c.mp3");
        audio.put("brick_url", "https://cdn.pixabay.com/download/audio/2022/03/24/audio_ce0e1c5fb5.mp3");
        audio.put("win_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_0d0e1b1d9e.mp3");
        audio.put("lose_url", "https://cdn.pixabay.com/download/audio/2022/03/15/audio_688cfb3a52.mp3");
        audio.put("music_url", "https://cdn.pixabay.com/download/audio/2022/03/10/audio_c8c8e1c4c0.mp3");
        audio.put("laser_url", "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3");
        audio.put("powerup_url", "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3");
        root.set("audio", audio);

        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_phrase", "¡Felicidades!\\nHas completado el nivel.");
        texts.put("defeat_phrase", "¡No te rindas!\\nInténtalo de nuevo.");
        texts.put("victory_title", "¡VICTORIA!");
        texts.put("defeat_title", "DERROTA");
        ArrayNode floatingWords = MAPPER.createArrayNode();
        floatingWords.add("¡GENIAL!"); floatingWords.add("¡ASOMBROSO!"); floatingWords.add("¡BOOM!");
        texts.set("floating_words", floatingWords);
        texts.put("floating_color_hex", "#00FFFF");
        texts.put("floating_font_size", 42);
        texts.put("show_particles_with_words", false);
        root.set("texts", texts);

        ASSETS = root;
    }

    private static ObjectNode createPowerUp(String type, double chance, int duration, String color, String icon) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("type", type); p.put("drop_chance", chance); p.put("duration", duration);
        p.put("color_hex", color); p.put("url_icon", icon);
        return p;
    }

    private BallBounceAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}