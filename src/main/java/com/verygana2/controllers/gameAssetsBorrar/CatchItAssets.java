package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class CatchItAssets {
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
        ObjectNode front = MAPPER.createObjectNode();
        ObjectNode frontTiling = MAPPER.createObjectNode(); frontTiling.put("x", 1.0); frontTiling.put("y", 1.0);
        front.set("Tiling", frontTiling);
        ObjectNode frontDir = MAPPER.createObjectNode(); frontDir.put("x", 1.0); frontDir.put("y", 0.0);
        front.set("Direction", frontDir);
        front.put("Speed", 0.2); front.put("Rotation", 0.0); front.put("Alpha", 0.0);
        front.put("ColorHex", "#FFFFFF"); front.put("SpriteUrl", ""); front.put("Enabled", false);
        shaderBg.set("Front", front);
        ObjectNode back = MAPPER.createObjectNode();
        ObjectNode backTiling = MAPPER.createObjectNode(); backTiling.put("x", 1.0); backTiling.put("y", 1.0);
        back.set("Tiling", backTiling);
        ObjectNode backDir = MAPPER.createObjectNode(); backDir.put("x", 0.0); backDir.put("y", 0.0);
        back.set("Direction", backDir);
        back.put("Speed", 0.0); back.put("Rotation", 0.0); back.put("Alpha", 1.0);
        back.put("ColorHex", "#FFFFFF");
        back.put("SpriteUrl", "https://placehold.co/1024x512/000033/FFFFFF.png?text=BG");
        shaderBg.set("Back", back);
        branding.set("shader_background_config", shaderBg);
        root.set("branding", branding);

        // texts
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡HAS GANADO!");
        texts.put("victory_phrase", "¡Has recogido todos los objetos de la lista!");
        texts.put("defeat_title", "HAS PERDIDO");
        texts.put("defeat_phrase", "Inténtalo de nuevo.");
        root.set("texts", texts);

        // game_config
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("duration", 40.0);
        gameConfig.put("spawn_rate", 1.2);
        gameConfig.put("lives", 3);
        root.set("game_config", gameConfig);

        // game
        ObjectNode game = MAPPER.createObjectNode();
        game.put("fall_speed_min", 2.0);
        game.put("fall_speed_max", 4.0);
        game.put("basket_speed", 15.0);
        game.put("shopping_list_size", 4);
        game.put("min_quantity", 2);
        game.put("max_quantity", 5);
        game.put("list_bg_image_url", "https://placehold.co/256x256/FFFFFF/000000.png?text=LISTSDADASDASDASDASDAD");
        game.put("basket_sprite_url", "https://placehold.co/256x128/00FF00/000000.png?text=BASKET");

        ArrayNode objects = MAPPER.createArrayNode();
        String[][] objectData = {
            {"item_1", "https://placehold.co/128x128/FF0000/FFFFFF.png?text=ITEM_1", "10", "1.0", "false"},
            {"item_2", "https://placehold.co/128x128/00FF00/FFFFFF.png?text=ITEM_2", "5", "0.8", "false"},
            {"item_3", "https://placehold.co/128x128/0000FF/FFFFFF.png?text=ITEM_3", "15", "1.2", "false"},
            {"item_4", "https://games.verygana.com/asset_tests/wp9751809-meme-pc-wallpapers.png", "20", "1.5", "false"},
            {"trash", "https://placehold.co/128x128/333333/FFFFFF.png?text=BOMB", "0", "1.0", "true"}
        };
        for (String[] od : objectData) {
            ObjectNode obj = MAPPER.createObjectNode();
            obj.put("id", od[0]);
            obj.put("sprite_url", od[1]);
            obj.put("score", Integer.parseInt(od[2]));
            obj.put("scale", Double.parseDouble(od[3]));
            if (Boolean.parseBoolean(od[4])) obj.put("is_obstacle", true);
            objects.add(obj);
        }
        game.set("objects", objects);
        root.set("game", game);

        // audio
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://games.verygana.com/asset_tests/music-guitar.wav");
        audio.put("positive_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("negative_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("spawn_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

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

    private CatchItAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}