package com.verygana2.controllers.gameAssetsBorrar.cali;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class SudokuAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "default"));

        // game_config
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("time_limit", 300);
        gameConfig.put("difficulty", "normal");
        gameConfig.put("max_errors", 3);
        gameConfig.put("empty_cells", 2);
        gameConfig.put("warning_threshold", 0.15);
        gameConfig.put("use_countdown", true);
        gameConfig.put("enable_powerups", true);

        ArrayNode levels = MAPPER.createArrayNode();
        int[][] levelData = {{1, 2, 300, 5}, {2, 2, 600, 5}};
        for (int[] ld : levelData) {
            ObjectNode lvl = MAPPER.createObjectNode();
            lvl.put("id", ld[0]); lvl.put("empty_cells", ld[1]); lvl.put("time_limit", ld[2]); lvl.put("max_errors", ld[3]);
            lvl.put("use_countdown", true); lvl.put("enable_powerups", true);
            levels.add(lvl);
        }
        gameConfig.set("levels", levels);
        root.set("game_config", gameConfig);

        // branding
        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_logo_url", "https://placehold.co/400x200/FF5733/FFFFFF.png?text=LOGO");
        images.put("main_logo_offset_y", 0.0);
        images.put("logo_watermark_url", "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK");
        images.put("logo_watermark_offset_y", 0.0);
        images.put("background_url", "https://placehold.co/1024x512/000033/FFFFFF.png?text=BG");
        images.put("background_color_hex", "#1a1a2e");
        images.put("cell_background_url", "https://placehold.co/128x128/FFFFFF/000000.png?text=CELL");
        images.put("button_background_url", "https://placehold.co/128x128/FFFFFF/000000.png?text=BTN");
        images.put("bomb_url", "https://placehold.co/128x128/FF0000/FFFFFF.png?text=BOMB");
        images.put("horizontal_url", "https://placehold.co/256x32/00FF00/000000.png?text=HORIZ");
        images.put("vertical_url", "https://placehold.co/32x256/00FF00/000000.png?text=VERT");
        branding.set("images", images);

        ObjectNode bgConfig = MAPPER.createObjectNode();
        ObjectNode front = MAPPER.createObjectNode();
        front.put("SpriteUrl", ""); front.put("ColorHex", "#FFFFFF"); front.put("Enabled", false);
        front.put("Speed", 0.2); front.put("Rotation", 0.0); front.put("LayoutMode", "TiledSquare"); front.put("AspectRatio", 1.0);
        bgConfig.set("Front", front);
        ObjectNode back = MAPPER.createObjectNode();
        back.put("SpriteUrl", "https://placehold.co/1024x512/000033/FFFFFF.png?text=BG");
        back.put("ColorHex", "#FFFFFF"); back.put("Enabled", true); back.put("Speed", 0.05);
        back.put("Rotation", 0.0); back.put("LayoutMode", "Stretched"); back.put("AspectRatio", 1.77);
        bgConfig.set("Back", back);
        branding.set("background_config", bgConfig);

        ObjectNode colors = MAPPER.createObjectNode();
        colors.put("selected_hex", "#FFD700"); colors.put("unselected_hex", "#FFFFFF");
        colors.put("text_normal_hex", "#000000"); colors.put("text_fixed_hex", "#000080");
        colors.put("grid_bg_hex", "#FFFFFF"); colors.put("cell_bg_hex", "#FFFFFF"); colors.put("btn_bg_hex", "#EEE");
        branding.set("colors", colors);
        root.set("branding", branding);

        // audio
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://games.verygana.com/asset_tests/music-guitar.wav");
        audio.put("click_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("error_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("rocket_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("whoosh_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("bomb_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("victory_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("lose_url", "https://games.verygana.com/asset_tests/slash.mp3");
        audio.put("win_game_url", "https://games.verygana.com/asset_tests/slash.mp3");
        root.set("audio", audio);

        // texts
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡EXCELENTE!");
        texts.put("victory_phrase", "Has resuelto el Sudoku correctamente.");
        texts.put("defeat_title", "GAME OVER");
        texts.put("defeat_phrase", "Se acabaron los intentos.");
        texts.put("label_difficulty", "Nivel");
        texts.put("label_time", "Tiempo");
        texts.put("label_errors", "Fallos");
        texts.put("label_score", "Llaves");
        root.set("texts", texts);

        // rewards
        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("coins_per_action", 20); //por numero colocado
        rewards.put("coins_on_completion", 200);
        root.set("rewards", rewards);

        // personalization
        ObjectNode personalization = MAPPER.createObjectNode();
        personalization.put("coin_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COIN");
        personalization.put("coin_count_url", "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COUNT");
        root.set("personalization", personalization);

        // game
        ObjectNode game = MAPPER.createObjectNode();
        ArrayNode tiles = MAPPER.createArrayNode();
        for (int i = 1; i <= 9; i++) {
            ObjectNode tile = MAPPER.createObjectNode();
            tile.put("url", "https://placehold.co/128x128/FFFFFF/000000.png?text=" + i);
            tiles.add(tile);
        }
        game.set("tiles", tiles);
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

    private SudokuAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}