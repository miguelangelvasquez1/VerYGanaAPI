package com.verygana2.controllers.gameAssetsBorrar.cali;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class WhackAMoleAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "default"));

        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_image_url", "https://games.verygana.com/asset_tests/redbull/redbull-logo.png");
        // images.put("logo_watermark_url", "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK");
        branding.set("images", images);

        ObjectNode shaderBg = MAPPER.createObjectNode();
        ObjectNode front = MAPPER.createObjectNode();
        front.put("Enabled", false);
        front.put("SpriteUrl", "");
        shaderBg.set("Front", front);
        ObjectNode back = MAPPER.createObjectNode();
        back.put("Alpha", 1.0);
        back.put("ColorHex", "#FFFFFF");
        back.put("SpriteUrl", "https://games.verygana.com/asset_tests/redbull/fondo/azul.jpg");
        shaderBg.set("Back", back);
        branding.set("shader_background_config", shaderBg);
        root.set("branding", branding);

        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡EXCELENTE!");
        texts.put("victory_phrase", "¡Has machacado a todos los topos!");
        texts.put("defeat_title", "INTÉNTALO DE NUEVO");
        texts.put("defeat_phrase", "¡No te rindas, sigue practicando!");
        root.set("texts", texts);

        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("duration", 60.0);
        gameConfig.put("gridRows", 3);
        gameConfig.put("gridCols", 2);
        gameConfig.put("audioDivisions", 6);
        gameConfig.put("spawnInterval", 0.8);
        gameConfig.put("moleLifetime", 2.0);
        gameConfig.put("pointsPerHit", 10);
        gameConfig.put("maxLives", 3);
        root.set("game_config", gameConfig);

        // ObjectNode personalization = MAPPER.createObjectNode();
        // personalization.put("coin_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COIN");
        // personalization.put("coin_count_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COUNT");
        // root.set("personalization", personalization);

        ObjectNode game = MAPPER.createObjectNode();
        game.put("holeSpriteUrl", "https://games.verygana.com/asset_tests/redbull/redbull-latacerrada.png");
        game.put("moleSpriteUrl", "https://games.verygana.com/asset_tests/redbull/redbull-lataabierta.png");
        game.put("hitSpriteUrl", "https://games.verygana.com/asset_tests/redbull/redbull-lataabierta.png");
        game.put("errorSpriteUrl", "https://games.verygana.com/asset_tests/redbull/redbull-latarota.png");
        game.put("holeColorHex", "#FFFFFF");
        game.put("moleColorHex", "#FFFFFF");
        game.put("hitColorHex", "#FFFFFF");
        game.put("errorColorHex", "#FFFFFF");
        game.put("showPreviewButtons", true);
        game.put("previewButtonSpriteUrl", "https://games.verygana.com/asset_tests/redbull/redbbull-playbutton.png");
        game.put("previewButtonColorHex", "#FFFFFF");
        root.set("game", game);

        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://games.verygana.com/asset_tests/redbull/redbull-tedaalas.mp3");
        audio.put("main_audio_url", "https://games.verygana.com/asset_tests/redbull/redbull-tedaalas.mp3");
        audio.put("hit_sfx_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("miss_sfx_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("win_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("lose_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("coins_per_action", 20); //por cada boton golpeado
        rewards.put("coins_on_completion", 200);
        root.set("rewards", rewards);

        // reward_popup
        ObjectNode rewardPopup = MAPPER.createObjectNode();
        rewardPopup.put("popup_title", "Recompensas desbloqueadas");

        ArrayNode products = MAPPER.createArrayNode();

        // Producto 1
        ObjectNode prod1 = MAPPER.createObjectNode();
        prod1.put("id", 1);
        prod1.put("name", "Membresia de 3 meses");
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

    private WhackAMoleAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}