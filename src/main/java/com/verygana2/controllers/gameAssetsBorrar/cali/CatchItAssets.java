package com.verygana2.controllers.gameAssetsBorrar.cali;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class CatchItAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "default"));

        // branding
        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_image_url", "https://placehold.co/400x200/FF5733/FFFFFF.png?text=LOGO");
        images.put("logo_watermark_url", "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK");
        branding.set("images", images);

        ObjectNode shaderBg = MAPPER.createObjectNode();
        ObjectNode front = MAPPER.createObjectNode();
        ObjectNode frontTiling = MAPPER.createObjectNode(); frontTiling.put("x", 1.0); frontTiling.put("y", 1.0);
        front.set("Tiling", frontTiling);
        ObjectNode frontDir = MAPPER.createObjectNode(); frontDir.put("x", 1.0); frontDir.put("y", 0.0);
        front.set("Direction", frontDir);
        front.put("Speed", 0.2); front.put("Rotation", 0.0); front.put("Alpha", 0.0);
        front.put("ColorHex", "#FFFFFF"); front.put("SpriteUrl", ""); front.put("Enabled", false);
        shaderBg.set("Front", front);
        ObjectNode back = MAPPER.createObjectNode();
        ObjectNode backTiling = MAPPER.createObjectNode(); backTiling.put("x", 1.0); backTiling.put("y", 1.0);
        back.set("Tiling", backTiling);
        ObjectNode backDir = MAPPER.createObjectNode(); backDir.put("x", 0.0); backDir.put("y", 0.0);
        back.set("Direction", backDir);
        back.put("Speed", 0.0); back.put("Rotation", 0.0); back.put("Alpha", 1.0);
        back.put("ColorHex", "#FFFFFF");
        back.put("SpriteUrl", "https://placehold.co/1024x512/000033/FFFFFF.png?text=BG");
        shaderBg.set("Back", back);
        branding.set("shader_background_config", shaderBg);
        root.set("branding", branding);

        // texts
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡HAS GANADO!");
        texts.put("victory_phrase", "¡Has recogido todos los objetos de la lista!");
        texts.put("defeat_title", "HAS PERDIDO");
        texts.put("defeat_phrase", "Inténtalo de nuevo.");
        root.set("texts", texts);

        // game_config
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("duration", 40.0);
        gameConfig.put("spawn_rate", 1.2);
        gameConfig.put("lives", 3);
        root.set("game_config", gameConfig);

        // game
        ObjectNode game = MAPPER.createObjectNode();
        game.put("fall_speed_min", 2.0);
        game.put("fall_speed_max", 4.0);
        game.put("basket_speed", 15.0);
        game.put("shopping_list_size", 4);
        game.put("min_quantity", 2);
        game.put("max_quantity", 5);
        game.put("list_bg_image_url", "https://placehold.co/256x256/FFFFFF/000000.png?text=LISTSDADASDASDASDASDAD");
        game.put("basket_sprite_url", "https://placehold.co/256x128/00FF00/000000.png?text=BASKET");

        ArrayNode objects = MAPPER.createArrayNode();
        String[][] objectData = {
            {"item_1", "https://placehold.co/128x128/FF0000/FFFFFF.png?text=ITEM_1", "10", "1.0", "false"},
            {"item_2", "https://placehold.co/128x128/00FF00/FFFFFF.png?text=ITEM_2", "5", "0.8", "false"},
            {"item_3", "https://placehold.co/128x128/0000FF/FFFFFF.png?text=ITEM_3", "15", "1.2", "false"},
            {"item_4", "https://games.verygana.com/asset_tests/wp9751809-meme-pc-wallpapers.png", "20", "1.5", "false"},
            {"trash", "https://placehold.co/128x128/333333/FFFFFF.png?text=BOMB", "0", "1.0", "true"}
        };
        for (String[] od : objectData) {
            ObjectNode obj = MAPPER.createObjectNode();
            obj.put("id", od[0]);
            obj.put("sprite_url", od[1]);
            obj.put("score", Integer.parseInt(od[2]));
            obj.put("scale", Double.parseDouble(od[3]));
            if (Boolean.parseBoolean(od[4])) obj.put("is_obstacle", true);
            objects.add(obj);
        }
        game.set("objects", objects);
        root.set("game", game);

        // audio
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://games.verygana.com/asset_tests/music-guitar.wav");
        audio.put("positive_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("negative_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("spawn_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

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

    private CatchItAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}