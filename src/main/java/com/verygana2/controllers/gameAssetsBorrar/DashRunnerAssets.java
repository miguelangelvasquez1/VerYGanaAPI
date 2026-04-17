package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class DashRunnerAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "default"));

        ObjectNode branding = MAPPER.createObjectNode();
        branding.put("main_logo_url", "https://games.verygana.com/asset_tests/redbull/redbull-logo.png");
        root.set("branding", branding);

        ObjectNode game = MAPPER.createObjectNode();
        game.put("character_id", 1);
        game.put("character_color", "#FFFFFF");
        game.put("key_spawn_probability", 0.18);
        game.set("background_phrases", MAPPER.createArrayNode());
        game.put("character_image_url", "");
        root.set("game", game);

        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("key_win_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("lose_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

        ObjectNode texts = MAPPER.createObjectNode();
        texts.set("game_over_messages", MAPPER.createArrayNode().add("¡Sigue intentándolo!"));
        root.set("texts", texts);

        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("keys_per_action", 5);
        root.set("rewards", rewards);

        ASSETS = root;
    }

    private DashRunnerAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS); }
        catch (Exception e) { return ASSETS.toString(); }
    }
}