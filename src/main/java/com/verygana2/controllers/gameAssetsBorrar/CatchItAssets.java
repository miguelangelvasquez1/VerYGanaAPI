package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class CatchItAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "CATCH_IT_DEFAULT"));

        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("duration", 60.0);
        gameConfig.put("spawn_rate", 1.2);
        gameConfig.put("lives", 3);
        root.set("game_config", gameConfig);

        ObjectNode game = MAPPER.createObjectNode();
        game.put("fall_speed_min", 2.0);
        game.put("fall_speed_max", 4.0);
        game.put("basket_speed", 15.0);
        game.put("shopping_list_size", 3);
        game.put("min_quantity", 2);
        game.put("max_quantity", 5);
        game.put("list_bg_image_url", "");
        game.put("basket_sprite_url", "https://cdn-icons-png.flaticon.com/512/2913/2913133.png");

        ArrayNode objects = MAPPER.createArrayNode();
        objects.add(createObject("apple", "https://cdn-icons-png.flaticon.com/512/415/415682.png", 10, 1.0, false));
        objects.add(createObject("banana", "https://cdn-icons-png.flaticon.com/512/765/765560.png", 10, 1.0, false));
        objects.add(createObject("orange", "https://cdn-icons-png.flaticon.com/512/590/590779.png", 10, 1.0, false));
        objects.add(createObject("burger", "https://cdn-icons-png.flaticon.com/512/1046/1046784.png", 15, 1.0, false));
        objects.add(createObject("trash", "https://cdn-icons-png.flaticon.com/512/484/484611.png", 0, 1.0, true));
        game.set("objects", objects);
        root.set("game", game);

        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://cdn.pixabay.com/download/audio/2022/03/10/audio_c8c8e1c4c0.mp3");
        audio.put("positive_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_0d0e1b1d9e.mp3");
        audio.put("negative_url", "https://cdn.pixabay.com/download/audio/2022/03/15/audio_688cfb3a52.mp3");
        audio.put("spawn_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_12b0c7443c.mp3");
        root.set("audio", audio);

        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡VICTORIA!");
        texts.put("victory_phrase", "¡Completaste tu lista!");
        texts.put("defeat_title", "DERROTA");
        texts.put("defeat_phrase", "¡Inténtalo de nuevo!");
        root.set("texts", texts);

        ASSETS = root;
    }

    private static ObjectNode createObject(String id, String url, int score, double scale, boolean isObstacle) {
        ObjectNode obj = MAPPER.createObjectNode();
        obj.put("id", id); obj.put("sprite_url", url); obj.put("score", score);
        obj.put("scale", scale); obj.put("is_obstacle", isObstacle);
        return obj;
    }

    private CatchItAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}