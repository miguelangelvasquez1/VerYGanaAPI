package com.verygana2.controllers.gameAssetsBorrar.cali;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class MemoryAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "default"));

        // game_config
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("start_pairs", 2);
        gameConfig.put("final_pairs", 10);
        gameConfig.put("game_duration", 120);
        gameConfig.put("bonus_per_pair", 5);
        gameConfig.put("preview_duration", 2);
        gameConfig.put("enable_lives", true);
        gameConfig.put("max_lives", 4);
        gameConfig.put("enable_powerups", true);
        gameConfig.put("power_up_chance", 0.3);
        gameConfig.put("bonus_time_amount", 15);

        ArrayNode levels = MAPPER.createArrayNode();
        double[][] levelData = {{1, 3, 0.3, 1.0, 1.0, 1.0}, {2, 6, 0.3, 1.0, 1.0, 1.0}, {3, 10, 0.2, 1.0, 1.5, 0.5}};
        String[] levelNames = {"Nivel 1", "Nivel 2", "Nivel 3"};
        for (int i = 0; i < 2; i++) {
            ObjectNode lvl = MAPPER.createObjectNode();
            lvl.put("id", (int) levelData[i][0]);
            lvl.put("stage_name", levelNames[i]);
            lvl.put("pair_count", (int) levelData[i][1]);
            lvl.put("power_up_chance", levelData[i][2]);
            lvl.put("weight_life", levelData[i][3]);
            lvl.put("weight_time", levelData[i][4]);
            lvl.put("weight_hint", levelData[i][5]);
            levels.add(lvl);
        }
        gameConfig.set("levels", levels);
        root.set("game_config", gameConfig);

        // branding
        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_logo_url", "https://games.verygana.com/asset_tests/fritolay/fritolay-logo.png");
        images.put("main_logo_offset_y", 0.0);
        // images.put("logo_watermark_url", "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK");
        images.put("logo_watermark_offset_y", 0.0);
        images.put("card_back_url", "https://placehold.co/256x256/3498db/ffffff.png?text=?");
        images.put("icon_life_url", "https://placehold.co/64x64/FF0000/FFFFFF.png?text=LIFE");
        images.put("icon_hint_url", "https://placehold.co/64x64/FFFF00/000000.png?text=HINT");
        images.put("icon_time_url", "https://placehold.co/64x64/00FF00/FFFFFF.png?text=TIME");
        branding.set("images", images);

        ObjectNode bgConfig = MAPPER.createObjectNode();
        ObjectNode front = MAPPER.createObjectNode();
        front.put("SpriteUrl", "https://games.verygana.com/asset_tests/fritolay/fritolay-fondo.jpeg"); front.put("ColorHex", "#FFFFFF"); front.put("Enabled", true);
        front.put("Speed", 0.2); front.put("Rotation", 0.0); front.put("LayoutMode", "TiledSquare"); front.put("AspectRatio", 1.0);
        bgConfig.set("Front", front);
        ObjectNode back = MAPPER.createObjectNode();
        back.put("SpriteUrl", "https://games.verygana.com/asset_tests/fritolay/fritolay-fondo.jpeg");
        back.put("ColorHex", ""); back.put("Enabled", false); back.put("Speed", 0.05);
        back.put("Rotation", 0.0); back.put("LayoutMode", "TiledSquare"); back.put("AspectRatio", 1.77);
        bgConfig.set("Back", back);
        branding.set("background_config", bgConfig);
        root.set("branding", branding);

        // audio
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://games.verygana.com/asset_tests/music-guitar.wav");
        audio.put("flip_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("success_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("error_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("powerup_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("victory_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("lose_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

        // texts
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡EXCELENTE!");
        texts.put("victory_phrase", "Has completado todos los niveles.");
        texts.put("defeat_title", "GAME OVER");
        texts.put("defeat_phrase", "¡Inténtalo de nuevo!");
        texts.put("label_time", "Tiempo");
        texts.put("label_attempts", "Intentos");
        texts.put("label_score", "Llaves");
        texts.put("label_level", "Nivel");
        root.set("texts", texts);

        // rewards
        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("coins_per_action", 20);
        rewards.put("coins_on_completion", 200);
        root.set("rewards", rewards);

        // personalization
        ObjectNode personalization = MAPPER.createObjectNode();
        personalization.put("coin_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COIN");
        personalization.put("coin_count_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COUNT");
        root.set("personalization", personalization);

        // game
        ObjectNode game = MAPPER.createObjectNode();
        ArrayNode cardImages = MAPPER.createArrayNode();
        String[] cardColors = {"margarita-negras-fondo.jpg", "cheesetris-fondo.jpg", "cheetos-fondo.jpg", "choclitos-fondo.jpg", "choclitos-picante-fondo.jpg", "detodito-bbq-fondo.jpg", "detodito-fondo.jpg", "doritos-flamin-fondo.jpg", "doritos-fondo.jpg", "margarita-fondo.jpg"};
        for (int i = 0; i < cardColors.length; i++) {
            ObjectNode card = MAPPER.createObjectNode();
            card.put("url", "https://games.verygana.com/asset_tests/fritolay/" + cardColors[i]);
            cardImages.add(card);
        }
        game.set("card_images", cardImages);
        root.set("game", game);

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

    private MemoryAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}