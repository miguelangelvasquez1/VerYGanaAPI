package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class TicTacToeAssets {
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
        game.set("round_win_phrases", MAPPER.createArrayNode());
        game.put("piece_logo_url", "");
        root.set("game", game);

        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("key_win_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("victory_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("game_over_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

        ObjectNode texts = MAPPER.createObjectNode();
        texts.set("game_over_messages", MAPPER.createArrayNode().add("¡Buena partida!"));
        root.set("texts", texts);

        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("keys_per_action", 3);
        rewards.put("keys_on_completion", 40);
        root.set("rewards", rewards);

        ASSETS = root;
    }

    private TicTacToeAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS); }
        catch (Exception e) { return ASSETS.toString(); }
    }
}