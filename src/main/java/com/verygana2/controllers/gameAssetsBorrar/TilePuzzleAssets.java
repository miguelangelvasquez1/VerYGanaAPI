package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class TilePuzzleAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "default"));

        ObjectNode branding = MAPPER.createObjectNode();
        branding.put("main_logo_url", "https://games.verygana.com/asset_tests/redbull/redbull-logo.png");
        branding.put("watermark_logo_url", "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK");
        root.set("branding", branding);

        ObjectNode game = MAPPER.createObjectNode();
        game.put("puzzle_image_url", "");
        root.set("game", game);

        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("key_win_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("victory_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

        ObjectNode texts = MAPPER.createObjectNode();
        texts.set("victory_messages", MAPPER.createArrayNode().add("¡Puzzle completado!"));
        root.set("texts", texts);

        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("keys_per_action", 5);
        rewards.put("keys_on_completion", 80);
        root.set("rewards", rewards);

        ASSETS = root;
    }

    private TilePuzzleAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS); }
        catch (Exception e) { return ASSETS.toString(); }
    }
}