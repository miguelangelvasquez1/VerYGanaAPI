package com.verygana2.controllers.gameAssetsBorrar.cali;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class TapToRotateAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "default"));

        // game_config
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("scroll_speed", 1.0);
        gameConfig.put("max_scroll_speed", 1.0);
        gameConfig.put("acceleration", 0.0);
        gameConfig.put("jump_force", 12.0);
        gameConfig.put("gravity_scale", 3.0);
        gameConfig.put("game_duration", 30);
        gameConfig.put("use_countdown", true);
        gameConfig.put("max_lives", 3);
        root.set("game_config", gameConfig);

        // branding
        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_logo_url", "https://placehold.co/400x200/FF5733/FFFFFF.png?text=LOGO");
        images.put("main_logo_offset_y", 50.0);
        images.put("logo_watermark_url", "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK");
        images.put("logo_watermark_offset_y", -50.0);
        branding.set("images", images);

        ObjectNode visuals = MAPPER.createObjectNode();
        visuals.put("player_url", "https://placehold.co/128x128/00FF00/000000.png?text=PLAYER");
        visuals.put("ground_url", "https://placehold.co/128x128/654321/FFFFFF.png?text=GROUND");
        visuals.put("ground_trap_url", "https://placehold.co/128x128/FF0000/FFFFFF.png?text=TRAP");
        visuals.put("coin_url", "https://placehold.co/128x128/FFFF00/000000.png?text=COIN");
        visuals.put("air_trap_url", "https://placehold.co/128x128/FF00FF/FFFFFF.png?text=MINE");
        visuals.put("bg_image_url", "https://placehold.co/1024x512/000033/FFFFFF.png?text=BG");
        visuals.put("death_wall_url", "https://placehold.co/512x512/660000/FFFFFF.png?text=DEATH");
        visuals.put("coin_scale", 1.2);
        visuals.put("air_trap_scale", 1.0);
        visuals.put("ground_trap_scale", 0.9);
        visuals.put("ground_texture_scale", 1.0);
        visuals.put("bg_texture_scale", 1.0);
        visuals.put("bg_solid_color", "#1a0b2e");
        visuals.put("ground_color", "#ffffff");
        visuals.put("dw_primary_color", "#ff0000");
        visuals.put("dw_secondary_color", "#550000");
        visuals.put("dw_bg_color", "#220000");
        visuals.put("dw_scroll_x", 0.5);
        visuals.put("dw_scroll_y", 0.5);
        visuals.put("dw_desphase_x", 0.1);
        visuals.put("dw_desphase_y", 0.1);
        branding.set("visuals", visuals);

        ObjectNode bgConfig = MAPPER.createObjectNode();
        ObjectNode front = MAPPER.createObjectNode();
        front.put("SpriteUrl", "https://placehold.co/512x512/FFFFFF/000000.png?text=PATTERN");
        front.put("ColorHex", "#FFFFFF80"); front.put("Enabled", true); front.put("Speed", 0.1);
        front.put("Rotation", 0.0); front.put("LayoutMode", "TiledSquare"); front.put("AspectRatio", 1.0);
        bgConfig.set("Front", front);
        ObjectNode back = MAPPER.createObjectNode();
        back.put("SpriteUrl", ""); back.put("ColorHex", "#0A0A1E"); back.put("Enabled", true); back.put("Speed", 0.0);
        back.put("Rotation", 0.0); back.put("LayoutMode", "Stretched"); back.put("AspectRatio", 1.77);
        bgConfig.set("Back", back);
        branding.set("background_config", bgConfig);
        root.set("branding", branding);

        // audio
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://games.verygana.com/asset_tests/music-guitar.wav");
        audio.put("jump_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("land_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("coin_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("death_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("victory_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("lose_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

        // texts
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡MISIÓN CUMPLIDA!");
        texts.put("victory_phrase", "Excelente trabajo, sobreviviste.");
        texts.put("defeat_title", "¡CUIDADO AHI!");
        texts.put("defeat_phrase", "Mejor suerte la próxima vez.");
        texts.put("label_score", "LLAVES");
        texts.put("label_time", "TIEMPO");
        texts.put("label_record", "RÉCORD");
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

    private TapToRotateAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}