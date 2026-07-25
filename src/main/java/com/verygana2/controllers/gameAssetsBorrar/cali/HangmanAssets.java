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

        // reward_popup
        ObjectNode rewardPopup = MAPPER.createObjectNode();
        rewardPopup.put("popup_title", "Recompensas desbloqueadas");

        ArrayNode products = MAPPER.createArrayNode();

        // Producto 1
        ObjectNode prod1 = MAPPER.createObjectNode();
        prod1.put("id", 1);
        prod1.put("name", "Membresia de 3 meses PlayStation plus");
        prod1.put("image_url", "https://cdn.verygana.com/public/products/commercial-2/1779407655456-52126342.png");
        prod1.put("image_message", "SUPER DESCUENTO 50%");
        prod1.put("commercial", "CommercialTest");
        prod1.put("regular_price", 89900);
        prod1.put("keys_message", "Con [[4.495]] llaves pagas [[SOLO 44.495 COP]]");
        prod1.put("rating", 4.3);
        prod1.put("max_keys_allowed", 4495);
        prod1.put("min_cash_cents", 4449500);
        prod1.put("stock", 10);
        prod1.put("category_name", "Videojuegos");
        products.add(prod1);

        // Producto 2
        ObjectNode prod2 = MAPPER.createObjectNode();
        prod2.put("id", 2);
        prod2.put("name", "Membresia de spotify");
        prod2.put("image_url", "https://cdn.verygana.com/public/products/commercial-2/1779412957370-daaebe1b.jpg");
        prod2.put("image_message", "SUPER DESCUENTO 50%");
        prod2.put("commercial", "CommercialTest");
        prod2.put("regular_price", 31900);
        prod2.put("keys_message", "Con [[1.595]] llaves pagas [[SOLO 15.595 COP]]");
        prod2.put("rating", 4.4);
        prod2.put("max_keys_allowed", 1595);
        prod2.put("min_cash_cents", 1559500);
        prod2.put("stock", 10);
        prod2.put("category_name", "Musica");
        products.add(prod2);

        // Producto 3
        ObjectNode prod3 = MAPPER.createObjectNode();
        prod3.put("id", 3);
        prod3.put("name", "Membresia Netflix");
        prod3.put("image_url", "https://cdn.verygana.com/public/products/commercial-2/1779413069127-850ba6b4.png");
        prod3.put("image_message", "SUPER DESCUENTO 50%");
        prod3.put("commercial", "CommercialTest");
        prod3.put("regular_price", 47900);
        prod3.put("keys_message", "Con [[2.395]] llaves pagas [[SOLO 23.950 COP]]");
        prod3.put("rating", 4.4);
        prod3.put("max_keys_allowed", 2395);
        prod3.put("min_cash_cents", 2395000);
        prod3.put("stock", 10);
        prod3.put("category_name", "Entretenimiento");
        products.add(prod3);

        rewardPopup.set("products", products);
        root.set("reward_popup", rewardPopup);

        ASSETS = root;
    }

    private HangmanAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}