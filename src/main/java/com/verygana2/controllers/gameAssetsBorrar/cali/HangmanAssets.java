package com.verygana2.controllers.gameAssetsBorrar.cali;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class HangmanAssets {
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
        images.put("keyboard_sprite_url", "https://placehold.co/64x64/00FF00/000000.png?text=KEY");
        branding.set("images", images);

        ObjectNode shaderBg = MAPPER.createObjectNode();
        ObjectNode back = MAPPER.createObjectNode();
        back.put("ColorHex", "#FFFFFF"); back.put("Speed", 0.2); back.put("Rotation", 0); back.put("Alpha", 1);
        ObjectNode backTiling = MAPPER.createObjectNode(); backTiling.put("x", 1); backTiling.put("y", 1);
        back.set("Tiling", backTiling);
        ObjectNode backDir = MAPPER.createObjectNode(); backDir.put("x", 1); backDir.put("y", 0);
        back.set("Direction", backDir);
        back.put("SpriteUrl", "https://placehold.co/1024x512/000033/FFFFFF.png?text=BG");
        shaderBg.set("Back", back);
        ObjectNode front = MAPPER.createObjectNode();
        front.put("ColorHex", "#FFFFFF00"); front.put("Speed", 0.5); front.put("Rotation", 0); front.put("Alpha", 0);
        ObjectNode frontTiling = MAPPER.createObjectNode(); frontTiling.put("x", 1); frontTiling.put("y", 1);
        front.set("Tiling", frontTiling);
        ObjectNode frontDir = MAPPER.createObjectNode(); frontDir.put("x", 1); frontDir.put("y", 0);
        front.set("Direction", frontDir);
        front.put("SpriteUrl", "");
        shaderBg.set("Front", front);
        branding.set("shader_background_config", shaderBg);
        branding.putNull("parallax_config");
        root.set("branding", branding);

        // game_config
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("time_limit", 60);
        gameConfig.put("difficulty", "normal");
        gameConfig.put("max_attempts", 6);
        root.set("game_config", gameConfig);

        // game
        ObjectNode game = MAPPER.createObjectNode();
        game.put("font_color_hex", "#FFFFFF");

        ArrayNode words = MAPPER.createArrayNode();
        String[][] wordData = {
            {"ABC", "Celestial Bodies", "50"},
            {"ABC", "Natural Satellite", "50"},
            {"ABC", "Above Us", "50"}
        };
        for (String[] w : wordData) {
            ObjectNode wordNode = MAPPER.createObjectNode();
            wordNode.put("word", w[0]); wordNode.put("hint", w[1]); wordNode.put("score", Integer.parseInt(w[2]));
            words.add(wordNode);
        }
        game.set("words", words);

        ArrayNode powerUps = MAPPER.createArrayNode();
        String[][] puData = {
            {"RevealLetter", "Hint", "#ffffffff", "50", "https://placehold.co/64x64/FFFF00/000000.png?text=HINT"},
            {"ZapOptions", "Zap", "#ffffffff", "30", "https://placehold.co/64x64/00FFFF/000000.png?text=ZAP"},
            {"ExtraLife", "Life", "#ffffffff", "100", "https://placehold.co/64x64/FF00FF/FFFFFF.png?text=LIFE"}
        };
        for (String[] pu : puData) {
            ObjectNode puNode = MAPPER.createObjectNode();
            puNode.put("type", pu[0]); puNode.put("display_name", pu[1]); puNode.put("color_hex", pu[2]);
            puNode.put("cost", Integer.parseInt(pu[3])); puNode.put("icon_url", pu[4]);
            powerUps.add(puNode);
        }
        game.set("power_ups_config", powerUps);

        ArrayNode hangmanUrls = MAPPER.createArrayNode();
        for (int i = 1; i <= 7; i++) {
            hangmanUrls.add("https://placehold.co/256x256/333333/FFFFFF.png?text=HANG_" + i);
        }
        game.set("hangman_progress_urls", hangmanUrls);
        root.set("game", game);

        // audio
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("victory_sound_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("defeat_sound_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("click_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("music_url", "https://games.verygana.com/asset_tests/music-guitar.wav");
        audio.put("reveal_sound_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("zap_sound_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("life_sound_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

        // texts
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_phrase", "Congratulations! You Won!");
        texts.put("victory_title", "VICTORY!");
        texts.put("defeat_phrase", "Try Again");
        texts.put("defeat_title", "GAME OVER");
        root.set("texts", texts);

        // rewards
        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("coins_per_action", 20);
        rewards.put("coins_on_completion", 100);
        root.set("rewards", rewards);

        // personalization
        ObjectNode personalization = MAPPER.createObjectNode();
        personalization.put("coin_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COIN");
        personalization.put("coin_count_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COUNT");
        root.set("personalization", personalization);

        ASSETS = root;
    }

    private HangmanAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}