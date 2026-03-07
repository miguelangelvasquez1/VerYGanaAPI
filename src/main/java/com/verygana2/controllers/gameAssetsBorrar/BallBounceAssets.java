package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class BallBounceAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "default"));

        // branding
        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_image_url", "https://placehold.co/400x200/FF5733/FFFFFF.png?text=LOGO");
        images.put("main_image_offset_y", 0);
        images.put("logo_watermark_url", "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK");
        images.put("logo_watermark_offset_y", 0);
        branding.set("images", images);

        ObjectNode shaderBg = MAPPER.createObjectNode();
        shaderBg.put("PrimaryColorHex", "#1a0b2e");
        shaderBg.put("SecondaryColorHex", "#2d1b4e");
        shaderBg.put("ParticleColorHex", "#432c7a");
        shaderBg.put("Speed", 0.5);
        shaderBg.put("Difficulty", 1.0);
        shaderBg.put("UseShader", true);
        ObjectNode front = MAPPER.createObjectNode();
        front.put("Enabled", false);
        shaderBg.set("Front", front);
        ObjectNode back = MAPPER.createObjectNode();
        back.put("SpriteUrl", "https://placehold.co/1024x512/000033/FFFFFF.png?text=BG");
        back.put("Enabled", true);
        shaderBg.set("Back", back);
        branding.set("shader_background_config", shaderBg);
        root.set("branding", branding);

        // game_config
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("difficulty", "Medium");
        gameConfig.put("game_duration_sec", 40);
        gameConfig.put("score_per_hit", 0);
        root.set("game_config", gameConfig);

        // game
        ObjectNode game = MAPPER.createObjectNode();
        game.put("ball_speed", 500.0);
        game.put("paddle_speed", 600.0);
        game.put("brick_rows", 5);
        game.put("brick_columns", 9);
        game.put("url_ball_texture", "https://placehold.co/64x64/FFFFFF/000000.png?text=BALL");
        game.put("url_paddle_texture", "https://placehold.co/256x64/00FF00/000000.png?text=PADDLE");

        // levels
        ArrayNode levels = MAPPER.createArrayNode();

        // Easy level
        ObjectNode easyLevel = MAPPER.createObjectNode();
        easyLevel.put("level_difficulty", "Easy");
        easyLevel.put("ball_speed_override", 450.0);
        easyLevel.put("timer_sec", 40);
        easyLevel.put("brick_rows", 2);
        easyLevel.put("brick_columns", 1);
        easyLevel.put("url_image", "https://games.verygana.com/asset_tests/wp9751809-meme-pc-wallpapers.png");
        ArrayNode easyPowerUps = MAPPER.createArrayNode();
        ObjectNode easyMultiBall = MAPPER.createObjectNode();
        easyMultiBall.put("type", "MultiBall"); easyMultiBall.put("drop_chance", 0.1); easyMultiBall.put("duration", 0);
        easyMultiBall.put("color_hex", "#FFFFFF"); easyMultiBall.put("url_icon", "https://placehold.co/64x64/FFFFFF/000000.png?text=MULTIBALL");
        easyPowerUps.add(easyMultiBall);
        ObjectNode easyPaddleGrow = MAPPER.createObjectNode();
        easyPaddleGrow.put("type", "PaddleGrow"); easyPaddleGrow.put("drop_chance", 0.1); easyPaddleGrow.put("duration", 8);
        easyPaddleGrow.put("color_hex", "#00FF00"); easyPaddleGrow.put("url_icon", "https://placehold.co/64x64/00FF00/000000.png?text=GROW");
        easyPowerUps.add(easyPaddleGrow);
        easyLevel.set("power_ups", easyPowerUps);
        levels.add(easyLevel);

        // Medium level
        ObjectNode medLevel = MAPPER.createObjectNode();
        medLevel.put("level_difficulty", "Medium");
        medLevel.put("ball_speed_override", 550.0);
        medLevel.put("timer_sec", 55);
        medLevel.put("brick_rows", 2);
        medLevel.put("brick_columns", 1);
        medLevel.put("url_image", "https://games.verygana.com/asset_tests/wp9751809-meme-pc-wallpapers.png");
        ArrayNode medPowerUps = MAPPER.createArrayNode();
        ObjectNode medMultiBall = MAPPER.createObjectNode();
        medMultiBall.put("type", "MultiBall"); medMultiBall.put("drop_chance", 0.08); medMultiBall.put("duration", 0);
        medMultiBall.put("color_hex", "#FFFFFF"); medMultiBall.put("url_icon", "https://placehold.co/64x64/FFFFFF/000000.png?text=MULTIBALL");
        medPowerUps.add(medMultiBall);
        ObjectNode medLaser = MAPPER.createObjectNode();
        medLaser.put("type", "Laser"); medLaser.put("drop_chance", 0.1); medLaser.put("duration", 7);
        medLaser.put("color_hex", "#FF0000"); medLaser.put("url_icon", "https://placehold.co/64x64/FF0000/FFFFFF.png?text=LASER");
        medPowerUps.add(medLaser);
        ObjectNode medLife = MAPPER.createObjectNode();
        medLife.put("type", "Life"); medLife.put("drop_chance", 0.05); medLife.put("duration", 0);
        medLife.put("color_hex", "#FF00FF"); medLife.put("url_icon", "https://placehold.co/64x64/FF00FF/FFFFFF.png?text=LIFE");
        medPowerUps.add(medLife);
        medLevel.set("power_ups", medPowerUps);
        levels.add(medLevel);

        // Hard level
        ObjectNode hardLevel = MAPPER.createObjectNode();
        hardLevel.put("level_difficulty", "Hard");
        hardLevel.put("ball_speed_override", 650.0);
        hardLevel.put("timer_sec", 80);
        hardLevel.put("brick_rows", 2);
        hardLevel.put("brick_columns", 1);
        hardLevel.put("url_image", "https://games.verygana.com/asset_tests/wp9751809-meme-pc-wallpapers.png");
        ArrayNode hardPowerUps = MAPPER.createArrayNode();
        ObjectNode hardMultiBall = MAPPER.createObjectNode();
        hardMultiBall.put("type", "MultiBall"); hardMultiBall.put("drop_chance", 0.08); hardMultiBall.put("duration", 0);
        hardMultiBall.put("color_hex", "#FFFFFF"); hardMultiBall.put("url_icon", "https://placehold.co/64x64/FFFFFF/000000.png?text=MULTIBALL");
        hardPowerUps.add(hardMultiBall);
        ObjectNode hardLaser = MAPPER.createObjectNode();
        hardLaser.put("type", "Laser"); hardLaser.put("drop_chance", 0.12); hardLaser.put("duration", 10);
        hardLaser.put("color_hex", "#FF0000"); hardLaser.put("url_icon", "https://placehold.co/64x64/FF0000/FFFFFF.png?text=LASER");
        hardPowerUps.add(hardLaser);
        ObjectNode hardSlow = MAPPER.createObjectNode();
        hardSlow.put("type", "SlowMotion"); hardSlow.put("drop_chance", 0.1); hardSlow.put("duration", 6);
        hardSlow.put("color_hex", "#00FFFF"); hardSlow.put("url_icon", "https://placehold.co/64x64/00FFFF/000000.png?text=SLOW");
        hardPowerUps.add(hardSlow);
        ObjectNode hardLife = MAPPER.createObjectNode();
        hardLife.put("type", "Life"); hardLife.put("drop_chance", 0.05); hardLife.put("duration", 0);
        hardLife.put("color_hex", "#FF00FF"); hardLife.put("url_icon", "https://placehold.co/64x64/FF00FF/FFFFFF.png?text=LIFE");
        hardPowerUps.add(hardLife);
        ObjectNode hardPaddleGrow = MAPPER.createObjectNode();
        hardPaddleGrow.put("type", "PaddleGrow"); hardPaddleGrow.put("drop_chance", 0.08); hardPaddleGrow.put("duration", 10);
        hardPaddleGrow.put("color_hex", "#00FF00"); hardPaddleGrow.put("url_icon", "https://placehold.co/64x64/00FF00/000000.png?text=GROW");
        hardPowerUps.add(hardPaddleGrow);
        hardLevel.set("power_ups", hardPowerUps);
        levels.add(hardLevel);

        game.set("levels", levels);

        // global power_ups
        ArrayNode globalPowerUps = MAPPER.createArrayNode();
        ObjectNode gMultiBall = MAPPER.createObjectNode();
        gMultiBall.put("type", "MultiBall"); gMultiBall.put("drop_chance", 0.1); gMultiBall.put("duration", 0);
        gMultiBall.put("color_hex", "#FFFFFF"); gMultiBall.put("url_icon", "https://placehold.co/64x64/FFFFFF/000000.png?text=MULTIBALL");
        globalPowerUps.add(gMultiBall);
        ObjectNode gPaddleGrow = MAPPER.createObjectNode();
        gPaddleGrow.put("type", "PaddleGrow"); gPaddleGrow.put("drop_chance", 0.1); gPaddleGrow.put("duration", 10);
        gPaddleGrow.put("color_hex", "#00FF00"); gPaddleGrow.put("url_icon", "https://placehold.co/64x64/00FF00/000000.png?text=GROW");
        globalPowerUps.add(gPaddleGrow);
        game.set("power_ups", globalPowerUps);
        root.set("game", game);

        // audio
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("hit_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("brick_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("win_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("lose_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("music_url", "https://games.verygana.com/asset_tests/music-guitar.wav");
        audio.put("laser_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("powerup_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

        // texts
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_phrase", "¡Felicidades!\nHas completado el nivel.");
        texts.put("defeat_phrase", "¡No te rindas!\nInténtalo de nuevo.");
        texts.put("victory_title", "¡VICTORIA!");
        texts.put("defeat_title", "DERROTA");
        ArrayNode floatingWords = MAPPER.createArrayNode();
        for (String w : new String[]{"¡GENIAL!", "¡ASOMBROSO!", "¡TOMA YA!", "¡BRUTAL!", "¡IMPARABLE!", "¡BOOM!"}) {
            floatingWords.add(w);
        }
        texts.set("floating_words", floatingWords);
        texts.put("floating_color_hex", "#00FFFF");
        texts.put("floating_font_size", 42);
        texts.put("show_particles_with_words", false);
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

    private BallBounceAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}