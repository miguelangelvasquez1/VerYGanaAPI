package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class DashRunnerAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();

        ObjectNode meta = MAPPER.createObjectNode();
        meta.put("brand_id", "default");
        meta.put("campaign_id", "17");
        root.set("meta", meta);

        ObjectNode branding = MAPPER.createObjectNode();
        branding.put("main_logo_url", "https://games.verygana.com/asset_tests/redbull/redbull-logo.png");
        root.set("branding", branding);

        ObjectNode game = MAPPER.createObjectNode();
        game.put("character_color", "#FFFFFF");
        game.put("key_spawn_probability", 0.18);
        game.set("background_phrases", MAPPER.createArrayNode().add("¡Sigue intentándolo!").add("¡No te rindas!").add("¡Tú puedes!"));
        game.put("character_image_url", "https://games.verygana.com/asset_tests/redbull/redbull-logo.png");
        root.set("game", game);

        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("key_win_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("lose_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

        ObjectNode texts = MAPPER.createObjectNode();
        texts.set("game_over_messages", MAPPER.createArrayNode().add("¡Sigue intentándolo!").add("Hola"));
        root.set("texts", texts);

        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("keys_per_action", 5);
        rewards.put("keys_on_completion", 5);
        root.set("rewards", rewards);

        // ==================== NUEVO NODO ====================
        ArrayNode productsData = MAPPER.createArrayNode();

        // Producto 1
        ObjectNode prod1 = MAPPER.createObjectNode();
        prod1.put("id", "prod_001");
        prod1.put("name", "Combo doble con papas");
        prod1.put("image_url", "https://games.verygana.com/asset_tests/redbull/redbull-logo.png");
        prod1.put("image_message", "50% Descuento");
        prod1.put("commercial", "BurgerMax");
        prod1.put("regular_price", 32000);
        prod1.put("keys_message", "Con [[1.600]] llaves pagas [[SOLO 16.000 COP]]");
        prod1.put("rating", 4.3);
        productsData.add(prod1);

        // Producto 2
        ObjectNode prod2 = MAPPER.createObjectNode();
        prod2.put("id", "prod_002");
        prod2.put("name", "Combo doble con papas 2");
        prod2.put("image_url", "https://games.verygana.com/asset_tests/redbull/redbull-logo.png");
        prod2.put("image_message", "50% Descuento");
        prod2.put("commercial", "BurgerMax");
        prod2.put("regular_price", 38000);
        prod2.put("keys_message", "Con [[1.900]] llaves pagas [[SOLO 19.000 COP]]");
        prod2.put("rating", 4.7);
        productsData.add(prod2);

        // Producto 3
        ObjectNode prod3 = MAPPER.createObjectNode();
        prod3.put("id", "prod_003");
        prod3.put("name", "Combo doble con papas 3");
        prod3.put("image_url", "https://games.verygana.com/asset_tests/redbull/redbull-logo.png");
        prod3.put("image_message", "50% Descuento");
        prod3.put("commercial", "BurgerMax");
        prod3.put("regular_price", 24000);
        prod3.put("keys_message", "Con [[1.200]] llaves pagas [[SOLO 12.000 COP]]");
        prod3.put("rating", 3.9);
        productsData.add(prod3);

        root.set("reward_popup", productsData);
        // ====================================================

        ASSETS = root;
    }

    private DashRunnerAssets() {}
    
    public static ObjectNode getAssets() { 
        return ASSETS; 
    }
    
    public static String getAssetsAsString() {
        try { 
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS); 
        }
        catch (Exception e) { 
            return ASSETS.toString(); 
        }
    }
}