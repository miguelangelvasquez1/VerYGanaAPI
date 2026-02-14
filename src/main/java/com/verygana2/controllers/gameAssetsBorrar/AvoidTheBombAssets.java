package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Configuración completa del juego Avoid The Bomb (Esquiva la Bomba / Corta Fruta)
 */
public final class AvoidTheBombAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "AVOID_BOMB_DEFAULT"));

        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("duration", 60);
        gameConfig.put("lives", 3);
        gameConfig.put("spawn_interval", 1.0);
        gameConfig.put("min_spawn_interval", 0.4);
        gameConfig.put("difficulty_decrease_step", 0.02);
        gameConfig.put("min_up_force", 13.0);
        gameConfig.put("max_up_force", 17.0);
        gameConfig.put("side_force", 2.5);
        gameConfig.put("gravity_multiplier", 1.0);
        gameConfig.put("good_points", 10);
        gameConfig.put("bonus_points", 50);
        gameConfig.put("freeze_duration", 5.0);
        gameConfig.put("double_score_duration", 10.0);
        gameConfig.put("freeze_overlay_color_hex", "#0000FF4D");
        root.set("game_config", gameConfig);

        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_image_url", "https://cdn-icons-png.flaticon.com/512/415/415682.png");
        images.put("logo_watermark_url", "https://cdn-icons-png.flaticon.com/512/3050/3050526.png");
        branding.set("images", images);

        ObjectNode shaderBg = MAPPER.createObjectNode();
        ObjectNode back = MAPPER.createObjectNode();
        back.put("Enabled", true);
        back.put("SpriteUrl", "https://img.freepik.com/free-vector/tropical-leaves-background_23-2148302992.jpg");
        back.put("ColorHex", "#FFFFFFFF");
        back.put("ScrollSpeedX", 0.05);
        back.put("ScrollSpeedY", 0.0);
        back.put("TilingX", 1.0);
        back.put("TilingY", 1.0);
        back.put("WaveSpeed", 0.0);
        back.put("WaveFrequency", 0.0);
        back.put("WaveAmplitude", 0.0);
        shaderBg.set("Back", back);
        ObjectNode front = MAPPER.createObjectNode();
        front.put("Enabled", false);
        front.put("SpriteUrl", "");
        front.put("ColorHex", "#FFFFFF00");
        shaderBg.set("Front", front);
        branding.set("shader_background_config", shaderBg);
        root.set("branding", branding);

        ObjectNode game = MAPPER.createObjectNode();
        ArrayNode goodObjects = MAPPER.createArrayNode();
        goodObjects.add("https://cdn-icons-png.flaticon.com/512/415/415682.png");
        goodObjects.add("https://cdn-icons-png.flaticon.com/512/765/765560.png");
        goodObjects.add("https://cdn-icons-png.flaticon.com/512/590/590779.png");
        game.set("good_objects_urls", goodObjects);

        ArrayNode bombObjects = MAPPER.createArrayNode();
        bombObjects.add("https://cdn-icons-png.flaticon.com/512/1670/1670870.png");
        game.set("bomb_objects_urls", bombObjects);

        ArrayNode bonusItems = MAPPER.createArrayNode();
        bonusItems.add(createBonus("https://cdn-icons-png.flaticon.com/512/2838/2838590.png", 1, 0.3));
        bonusItems.add(createBonus("https://cdn-icons-png.flaticon.com/512/833/833472.png", 3, 0.2));
        game.set("bonus_items", bonusItems);

        game.put("click_effect_url", "");
        game.put("explosion_effect_url", "");
        root.set("game", game);

        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://cdn.pixabay.com/download/audio/2022/03/10/audio_c8c8e1c4c0.mp3");
        audio.put("win_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_0d0e1b1d9e.mp3");
        audio.put("lose_url", "https://cdn.pixabay.com/download/audio/2022/03/15/audio_688cfb3a52.mp3");
        audio.put("bomb_url", "https://cdn.pixabay.com/download/audio/2022/03/24/audio_ce0e1c5fb5.mp3");
        audio.put("normal_item_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_12b0c7443c.mp3");
        audio.put("bonus_item_url", "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3");
        audio.put("click_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_12b0c7443c.mp3");
        root.set("audio", audio);

        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "VICTORY");
        texts.put("victory_phrase", "You made it!");
        texts.put("defeat_title", "GAME OVER");
        texts.put("defeat_phrase", "Try again!");
        root.set("texts", texts);

        ASSETS = root;
    }

    private static ObjectNode createBonus(String url, int effectId, double weight) {
        ObjectNode bonus = MAPPER.createObjectNode();
        bonus.put("url", url);
        bonus.put("effect_id", effectId);
        bonus.put("weight", weight);
        return bonus;
    }

    private AvoidTheBombAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}