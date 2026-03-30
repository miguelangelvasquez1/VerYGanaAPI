package com.verygana2.controllers.gameAssetsBorrar;

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

        ASSETS = root;
    }

    private SudokuAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}