package com.verygana2.controllers.gameAssetsBorrar.cali;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class BalloonLiftAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "0001"));

        // game_config
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("time_limit", 30);
        gameConfig.put("difficulty", "normal");
        gameConfig.put("max_attempts", 3);
        root.set("game_config", gameConfig);

        // branding
        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_logo_url", "https://games.verygana.com/asset_tests/aseguradora/sura-logo-blanco-png.png");
        images.put("main_logo_offset_y", 100);
        // images.put("logo_watermark_url", "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK");
        images.put("logo_watermark_offset_y", -200);
        branding.set("images", images);

        ObjectNode shaderBg = MAPPER.createObjectNode();
        ObjectNode back = MAPPER.createObjectNode();
        back.put("SpriteUrl", "https://games.verygana.com/asset_tests/aseguradora/carretera.jpg");
        back.put("Enabled", true);
        back.put("ColorHex", "#FFFFFFFF");
        back.put("Speed", 0.0);
        back.put("Alpha", 1.0);
        ObjectNode backTiling = MAPPER.createObjectNode(); backTiling.put("x", 1); backTiling.put("y", 1);
        back.set("Tiling", backTiling);
        ObjectNode backDirection = MAPPER.createObjectNode(); backDirection.put("x", 1); backDirection.put("y", 0);
        back.set("Direction", backDirection);
        back.put("Rotation", 0);
        shaderBg.set("Back", back);
        ObjectNode frontNode = MAPPER.createObjectNode();
        frontNode.put("SpriteUrl", "");
        frontNode.put("Enabled", false);
        frontNode.put("ColorHex", "#FFFFFF00");
        frontNode.put("Speed", 0.0);
        frontNode.put("Alpha", 0.0);
        shaderBg.set("Front", frontNode);
        branding.set("shader_background_config", shaderBg);
        root.set("branding", branding);

        // game
        ObjectNode game = MAPPER.createObjectNode();
        ObjectNode gameImages = MAPPER.createObjectNode();
        gameImages.put("main_image_url", "https://games.verygana.com/asset_tests/aseguradora/carro.png");
        // gameImages.put("parallax_image_url", "https://placehold.co/512x256/00FF00/FFFFFF.png?text=PARALLAX");
        game.set("images", gameImages);
        game.set("stages", MAPPER.createArrayNode());
        game.set("obstacle_models_urls", MAPPER.createArrayNode());
        ArrayNode obstacleSprites = MAPPER.createArrayNode();
        obstacleSprites.add("https://games.verygana.com/asset_tests/aseguradora/charco.png");
        obstacleSprites.add("https://games.verygana.com/asset_tests/aseguradora/cono.png");
        obstacleSprites.add("https://games.verygana.com/asset_tests/aseguradora/multa.png");
        game.set("obstacle_sprite_urls", obstacleSprites);
        game.put("coin_model_url", "");
        game.set("power_ups_config", MAPPER.createArrayNode());
        ObjectNode attributes = MAPPER.createObjectNode();
        attributes.put("lift_force", 4.0);
        attributes.put("horizontal_force", 10.0);
        attributes.put("max_velocity", 8.0);
        attributes.put("level_duration", 60); // no sirve?
        attributes.put("gravity", 1.0);
        attributes.put("obstacle_spawn_interval", 1.5);
        attributes.put("obstacle_speed", 0.0);
        attributes.put("sway_frequency", 0.0);
        attributes.put("sway_magnitude", 0.0);
        game.set("attributes", attributes);
        root.set("game", game);

        // audio
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://games.verygana.com/asset_tests/music-guitar.wav");
        audio.put("victory_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("lose_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("coin_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("obstacle_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("powerup_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("shield_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

        // texts
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡VICTORIA!");
        texts.put("victory_phrase", "¡Felicidades, has ganado!");
        texts.put("defeat_title", "DERROTA");
        texts.put("defeat_phrase", "¡No te rindas, inténtalo de nuevo!");
        root.set("texts", texts);

        // rewards
        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("coins_per_action", 20);
        rewards.put("coins_on_completion", 200);
        root.set("rewards", rewards);

        // personalization
        ObjectNode personalization = MAPPER.createObjectNode();
        // personalization.put("coin_url", "https://placehold.co/500x500/FFD700/FFFFFF.png?text=COIN");
        // personalization.put("coin_count_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COUNT");
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

    private BalloonLiftAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}