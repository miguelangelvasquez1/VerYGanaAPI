package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class MemoryMatchAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();

        // meta
        ObjectNode meta = MAPPER.createObjectNode();
        meta.put("brand_id", "");
        meta.put("campaign_id", "");
        root.set("meta", meta);

        // branding
        ObjectNode branding = MAPPER.createObjectNode();
        branding.put("main_logo_url", "");
        branding.put("watermark_logo_url", "https://games.verygana.com/asset_tests/redbull/redbull-logo.png");
        root.set("branding", branding);

        // game_config
        root.set("game_config", MAPPER.createObjectNode()); // vacío por ahora

        // game → card_images
        ObjectNode game = MAPPER.createObjectNode();
        ArrayNode cardImages = MAPPER.createArrayNode();

        String[] imageUrls = {
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png",
            "https://games.verygana.com/asset_tests/redbull/redbull-logo.png"
        };

        for (String url : imageUrls) {
            ObjectNode card = MAPPER.createObjectNode();
            card.put("url", url);
            cardImages.add(card);
        }

        game.set("card_images", cardImages);
        root.set("game", game);

        // audio
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("key_win_url", "");
        audio.put("victory_url", "");
        root.set("audio", audio);

        // texts
        ObjectNode texts = MAPPER.createObjectNode();
        ArrayNode victoryMessages = MAPPER.createArrayNode();
        victoryMessages.add("¡Felicidades! Bancolombia te saluda¡");
        texts.set("victory_messages", victoryMessages);
        root.set("texts", texts);

        // rewards
        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("keys_per_action", 1);
        rewards.put("keys_on_completion", 1);
        root.set("rewards", rewards);

        // reward_popup
        ObjectNode rewardPopup = MAPPER.createObjectNode();
        rewardPopup.put("popup_title", "Recompensas desbloqueadas");

        ArrayNode products = MAPPER.createArrayNode();

        // Producto 1
        ObjectNode prod1 = MAPPER.createObjectNode();
        prod1.put("id", "prod_001");
        prod1.put("name", "Combo doble con papas");
        prod1.put("image_url", "https://games.verygana.com/asset_tests/redbull/redbull-logo.png");
        prod1.put("image_message", "SUPER DESCUENTO");
        prod1.put("commercial", "BurgerMax");
        prod1.put("regular_price", 32000);
        prod1.put("keys_message", "Con [[1.600]] llaves pagas [[SOLO 16.000 COP]]");
        prod1.put("rating", 4.3);
        prod1.put("max_keys_allowed", 1600);
        prod1.put("min_cash_cents", 160000);
        prod1.put("stock", 10);
        prod1.put("category_name", "Food");
        products.add(prod1);

        // Producto 2
        ObjectNode prod2 = MAPPER.createObjectNode();
        prod2.put("id", "prod_002");
        prod2.put("name", "Delux Truffle Combo");
        prod2.put("image_url", "https://games.verygana.com/asset_tests/redbull/redbull-logo.png");
        prod2.put("image_message", "SUPER DESCUENTO");
        prod2.put("commercial", "BurgerMax");
        prod2.put("regular_price", 45000);
        prod2.put("keys_message", "Con [[2.250]] llaves pagas [[SOLO 22.500 COP]]");
        prod2.put("rating", 4.3);
        prod2.put("max_keys_allowed", 1600);
        prod2.put("min_cash_cents", 160000);
        prod2.put("stock", 10);
        prod2.put("category_name", "Food");
        products.add(prod2);

        // Producto 3
        ObjectNode prod3 = MAPPER.createObjectNode();
        prod3.put("id", "prod_003");
        prod3.put("name", "Combo doble con papas");
        prod3.put("image_url", "https://games.verygana.com/asset_tests/redbull/redbull-logo.png");
        prod3.put("image_message", "SUPER DESCUENTO");
        prod3.put("commercial", "BurgerMax");
        prod3.put("regular_price", 38000);
        prod3.put("keys_message", "Con [[1.900]] llaves pagas [[SOLO 19.000 COP]]");
        prod3.put("rating", 4.3);
        prod3.put("max_keys_allowed", 1600);
        prod3.put("min_cash_cents", 160000);
        prod3.put("stock", 10);
        prod3.put("category_name", "Food");
        products.add(prod3);

        rewardPopup.set("products", products);
        root.set("reward_popup", rewardPopup);

        ASSETS = root;
    }

    private MemoryMatchAssets() {}

    public static ObjectNode getAssets() {
        return ASSETS;
    }

    public static String getAssetsAsString() {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) {
            return ASSETS.toString();
        }
    }
}