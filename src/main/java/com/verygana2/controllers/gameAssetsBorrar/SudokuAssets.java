package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Configuración completa del juego Sudoku
 */
public final class SudokuAssets {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();

        /* ================= META ================= */
        ObjectNode meta = MAPPER.createObjectNode();
        meta.put("brand_id", "SUDOKU_DEFAULT");
        root.set("meta", meta);

        /* ================= GAME_CONFIG ================= */
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("time_limit", 300);
        gameConfig.put("difficulty", "normal");
        gameConfig.put("max_errors", 3);
        gameConfig.put("empty_cells", 40);
        gameConfig.put("warning_threshold", 0.15);
        gameConfig.put("use_countdown", true);
        root.set("game_config", gameConfig);

        /* ================= BRANDING ================= */
        ObjectNode branding = MAPPER.createObjectNode();

        // Images
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_logo_url", "https://cdn-icons-png.flaticon.com/512/2917/2917242.png");
        images.put("main_logo_offset_y", 50.0);
        images.put("logo_watermark_url", "https://cdn-icons-png.flaticon.com/512/5337/5337564.png");
        images.put("logo_watermark_offset_y", 0.0);
        images.put("background_url", "https://img.freepik.com/free-vector/white-abstract-background_23-2148806276.jpg");
        images.put("background_color_hex", "#F5F5F5");
        images.put("cell_background_url", "");
        images.put("button_background_url", "");
        branding.set("images", images);

        // Background Config
        ObjectNode backgroundConfig = MAPPER.createObjectNode();
        ObjectNode front = MAPPER.createObjectNode();
        front.put("SpriteUrl", "");
        front.put("ColorHex", "#FFFFFF40");
        front.put("Enabled", false);
        front.put("Speed", 0.3);
        front.put("Rotation", 0);
        front.put("LayoutMode", "TiledSquare");
        front.put("AspectRatio", 1.0);
        backgroundConfig.set("Front", front);

        ObjectNode back = MAPPER.createObjectNode();
        back.put("SpriteUrl", "");
        back.put("ColorHex", "#E8F4F8");
        back.put("Enabled", true);
        back.put("Speed", 0.1);
        back.put("Rotation", 0);
        back.put("LayoutMode", "Stretched");
        back.put("AspectRatio", 1.77);
        backgroundConfig.set("Back", back);
        branding.set("background_config", backgroundConfig);

        // Colors
        ObjectNode colors = MAPPER.createObjectNode();
        colors.put("selected_hex", "#FFD700");
        colors.put("unselected_hex", "#FFFFFF");
        colors.put("text_normal_hex", "#2C3E50");
        colors.put("text_fixed_hex", "#34495E");
        colors.put("grid_bg_hex", "#FFFFFF");
        colors.put("cell_bg_hex", "#F8F9FA");
        colors.put("btn_bg_hex", "#E3F2FD");
        branding.set("colors", colors);

        root.set("branding", branding);

        /* ================= GAME ================= */
        ObjectNode game = MAPPER.createObjectNode();
        
        // Tiles (números personalizados, vacío = usar números default)
        ArrayNode tiles = MAPPER.createArrayNode();
        for (int i = 0; i < 9; i++) {
            ObjectNode tile = MAPPER.createObjectNode();
            tile.put("url", ""); // Vacío = usar números por defecto
            tiles.add(tile);
        }
        game.set("tiles", tiles);
        root.set("game", game);

        /* ================= AUDIO ================= */
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://cdn.pixabay.com/download/audio/2022/03/10/audio_c8c8e1c4c0.mp3");
        audio.put("click_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_12b0c7443c.mp3");
        audio.put("error_url", "https://cdn.pixabay.com/download/audio/2022/03/15/audio_688cfb3a52.mp3");
        audio.put("rocket_url", "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3");
        audio.put("whoosh_url", "https://cdn.pixabay.com/download/audio/2021/08/09/audio_bb630cc098.mp3");
        audio.put("bomb_url", "https://cdn.pixabay.com/download/audio/2022/03/24/audio_ce0e1c5fb5.mp3");
        audio.put("victory_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_0d0e1b1d9e.mp3");
        audio.put("lose_url", "https://cdn.pixabay.com/download/audio/2022/03/15/audio_688cfb3a52.mp3");
        audio.put("win_game_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_0d0e1b1d9e.mp3");
        root.set("audio", audio);

        /* ================= TEXTS ================= */
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡VICTORIA!");
        texts.put("victory_phrase", "Nivel Completado");
        texts.put("defeat_title", "DERROTA");
        texts.put("defeat_phrase", "Inténtalo de nuevo");
        texts.put("label_difficulty", "Dificultad");
        texts.put("label_time", "Tiempo");
        texts.put("label_errors", "Errores");
        texts.put("label_score", "Llaves");
        root.set("texts", texts);

        ASSETS = root;
    }

    private SudokuAssets() {}

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