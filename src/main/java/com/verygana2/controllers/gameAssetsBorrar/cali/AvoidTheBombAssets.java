package com.verygana2.controllers.gameAssetsBorrar.cali;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class AvoidTheBombAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "default"));

        // branding
        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_image_url", "https://placehold.co/400x200/FF5733/FFFFFF.png?text=LOGO");
        images.put("logo_watermark_url", "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK");
        branding.set("images", images);

        ObjectNode shaderBg = MAPPER.createObjectNode();
        ObjectNode back = MAPPER.createObjectNode();
        back.put("Enabled", true);
        back.put("SpriteUrl", "https://placehold.co/1024x512/000033/FFFFFF.png?text=BG");
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
        front.put("ScrollSpeedX", 0.1);
        front.put("ScrollSpeedY", 0.0);
        front.put("TilingX", 1.0);
        front.put("TilingY", 1.0);
        shaderBg.set("Front", front);
        branding.set("shader_background_config", shaderBg);
        root.set("branding", branding);

        // game_config
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("duration", 30);
        gameConfig.put("lives", 4);
        gameConfig.put("spawn_interval", 1.0);
        gameConfig.put("min_spawn_interval", 0.4);
        gameConfig.put("difficulty_decrease_step", 0.02);
        gameConfig.put("min_up_force", 13.0);
        gameConfig.put("max_up_force", 17.0);
        gameConfig.put("side_force", 2.5);
        gameConfig.put("gravity_multiplier", 1.0);
        gameConfig.put("actions_to_complete", 30); //objetos que el jugador debe recoger para completar el juego
        gameConfig.put("freeze_duration", 5.0);
        gameConfig.put("freeze_overlay_color_hex", "#0000FF4D");
        gameConfig.put("good_model_scale", 1.0);
        gameConfig.put("bad_model_scale", 1.0);
        gameConfig.put("bonus_model_scale", 0.5);
        root.set("game_config", gameConfig);

        // game
        // ObjectNode game = MAPPER.createObjectNode();
        // ArrayNode goodObjects = MAPPER.createArrayNode();
        // ObjectNode duck = MAPPER.createObjectNode();
        // duck.put("url", "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/Duck/glTF-Binary/Duck.glb");
        // duck.put("scale", 1.0);
        // goodObjects.add(duck);
        // ObjectNode avocado = MAPPER.createObjectNode();
        // avocado.put("url", "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/Avocado/glTF-Binary/Avocado.glb");
        // avocado.put("scale", 25.0);
        // goodObjects.add(avocado);
        // game.set("good_objects", goodObjects);

        // ArrayNode bombObjects = MAPPER.createArrayNode();
        // ObjectNode bottle = MAPPER.createObjectNode();
        // bottle.put("url", "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/WaterBottle/glTF-Binary/WaterBottle.glb");
        // bottle.put("scale", 5);
        // bombObjects.add(bottle);
        // game.set("bomb_objects", bombObjects);

        // ArrayNode bonusItems = MAPPER.createArrayNode();
        // int[] effectIds = {1, 2, 3};
        // double[] weights = {0.4, 0.3, 0.3};
        // for (int i = 0; i < 3; i++) {
        //     ObjectNode bonus = MAPPER.createObjectNode();
        //     bonus.put("url", "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/Box/glTF-Binary/Box.glb");
        //     bonus.put("effect_id", effectIds[i]);
        //     bonus.put("weight", weights[i]);
        //     bonus.put("scale", 2.0);
        //     bonusItems.add(bonus);
        // }
        // game.set("bonus_items", bonusItems);
        // game.put("click_effect_url", "");
        // game.put("explosion_effect_url", "");
        // root.set("game", game);

        // audio
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://games.verygana.com/asset_tests/music-guitar.wav");
        audio.put("win_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("lose_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("bomb_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("normal_item_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("bonus_item_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("click_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

        // texts
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "VICTORY test");
        texts.put("victory_phrase", "You made it! test");
        texts.put("defeat_title", "GAME OVER test");
        texts.put("defeat_phrase", "Try again! test");
        root.set("texts", texts);

        // rewards
        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("coins_per_action", 20); // por cada vez que coge una fruta
        rewards.put("coins_on_completion", 200);
        rewards.put("combo_multiplier", 0.0);
        root.set("rewards", rewards);

        // personalization
        ObjectNode personalization = MAPPER.createObjectNode();
        personalization.put("coin_url", "https://placehold.co/500x500/FFD700/FFFFFF.png?text=COIN");
        personalization.put("coin_count_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COUNT");
        root.set("personalization", personalization);

        // particleEffects
        ArrayNode particles = MAPPER.createArrayNode();
        String[][] particleData = {
            {"ex", "Explosion", "#FF5500", "30", "0.4", "0.8", "8.0", ""},
            {"sh", "Expand", "#00FFFF", "10", "0.3", "0.8", "5.0", "https://static.vecteezy.com/system/resources/thumbnails/048/301/774/small/white-star-decoration-on-christmas-tree-classic-ornament-for-winter-holiday-and-trendy-interior-png.png"},
            {"ft", "Fountain", "#FFD700", "10", "0.5", "2.5", "5.0", "https://www.pngall.com/wp-content/uploads/5/Plain-Game-Gold-Coin-PNG-File.png"},
            {"ob", "Orbital", "#00FF00", "10", "0.2", "1.0", "2.0", ""},
            {"im", "Implosion", "#FF00FF", "50", "0.3", "0.8", "12.0", ""},
            {"rs", "Rising", "#AAAAAA", "25", "0.6", "2.2", "2.5", ""}
        };
        for (String[] p : particleData) {
            ObjectNode particle = MAPPER.createObjectNode();
            particle.put("id", p[0]);
            particle.put("effect_type", p[1]);
            particle.put("color_hex", p[2]);
            particle.put("count", Integer.parseInt(p[3]));
            particle.put("start_size", Double.parseDouble(p[4]));
            particle.put("lifetime", Double.parseDouble(p[5]));
            particle.put("speed", Double.parseDouble(p[6]));
            particle.put("sprite_url", p[7]);
            particles.add(particle);
        }
        root.set("particleEffects", particles);

        ASSETS = root;
    }

    private AvoidTheBombAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}