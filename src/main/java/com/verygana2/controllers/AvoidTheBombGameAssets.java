package com.verygana2.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class AvoidTheBombGameAssets {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final ObjectNode ASSETS;

    static {
        ASSETS = MAPPER.createObjectNode();

        // game_config
        ObjectNode gameConfig = ASSETS.putObject("game_config");
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

        // branding
        ObjectNode branding = ASSETS.putObject("branding");

        ObjectNode brandingImages = branding.putObject("images");
        brandingImages.put("main_image_url", "https://img.freepik.com/vector-premium/bomba-dibujos-animados_6317-1596.jpg");
        brandingImages.put("logo_watermark_url", "https://img.freepik.com/vector-premium/bomba-dibujos-animados_6317-1596.jpg");

        ObjectNode shaderBackgroundConfig = branding.putObject("shader_background_config");

        ObjectNode back = shaderBackgroundConfig.putObject("Back");
        back.put("Enabled", true);
        back.put("SpriteUrl", "");
        back.put("ColorHex", "#FFFFFFFF");
        back.put("ScrollSpeedX", 0.05);
        back.put("ScrollSpeedY", 0.0);
        back.put("TilingX", 1.0);
        back.put("TilingY", 1.0);
        back.put("WaveSpeed", 0.0);
        back.put("WaveFrequency", 0.0);
        back.put("WaveAmplitude", 0.0);

        ObjectNode front = shaderBackgroundConfig.putObject("Front");
        front.put("Enabled", false);
        front.put("SpriteUrl", "");
        front.put("ColorHex", "#FFFFFF00");
        front.put("ScrollSpeedX", 0.1);
        front.put("ScrollSpeedY", 0.0);
        front.put("TilingX", 1.0);
        front.put("TilingY", 1.0);

        // game
        ObjectNode game = ASSETS.putObject("game");

        // good_objects_urls (array vacío)
        game.set("good_objects_urls", MAPPER.createArrayNode());

        // bomb_objects_urls (array vacío)
        game.set("bomb_objects_urls", MAPPER.createArrayNode());

        // bonus_items
        ArrayNode bonusItems = game.putArray("bonus_items");

        bonusItems.addObject()
                .put("url", "")
                .put("effect_id", 1)
                .put("weight", 0.4);

        game.put("click_effect_url", "https://www.epidemicsound.com/es/sound-effects/tracks/572866e7-0fdf-45b4-ba83-00066e59a07f/");
        game.put("explosion_effect_url", "https://www.epidemicsound.com/es/sound-effects/tracks/572866e7-0fdf-45b4-ba83-00066e59a07f/");

        // audio
        ObjectNode audio = ASSETS.putObject("audio");
        audio.put("music_url", "");
        audio.put("win_url", "");
        audio.put("lose_url", "");
        audio.put("bomb_url", "");
        audio.put("normal_item_url", "");
        audio.put("bonus_item_url", "");
        audio.put("click_url", "");

        // texts
        ObjectNode texts = ASSETS.putObject("texts");
        texts.put("victory_title", "VICTORY");
        texts.put("victory_phrase", "You made it!");
        texts.put("defeat_title", "GAME OVER");
        texts.put("defeat_phrase", "Try again!");

        // particleEffects
        ArrayNode particleEffects = ASSETS.putArray("particleEffects");

        particleEffects.addObject()
                .put("id", "ex")
                .put("effect_type", "Explosion")
                .put("color_hex", "#FF5500")
                .put("count", 30)
                .put("start_size", 0.4)
                .put("lifetime", 0.8)
                .put("speed", 8.0)
                .put("sprite_url", "https://www.epidemicsound.com/es/sound-effects/tracks/572866e7-0fdf-45b4-ba83-00066e59a07f/");
    }

    private AvoidTheBombGameAssets() {
    }
}