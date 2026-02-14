package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Configuración completa del juego Memory (Juego de Memoria)
 */
public final class MemoryAssets {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();

        /* ================= META ================= */
        ObjectNode meta = MAPPER.createObjectNode();
        meta.put("brand_id", "MEMORY_DEFAULT");
        root.set("meta", meta);

        /* ================= GAME_CONFIG ================= */
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("start_pairs", 3);
        gameConfig.put("final_pairs", 10);
        gameConfig.put("game_duration", 180);
        gameConfig.put("bonus_per_pair", 5);
        gameConfig.put("preview_duration", 3);
        gameConfig.put("enable_lives", true);
        gameConfig.put("max_lives", 3);
        gameConfig.put("enable_powerups", true);
        gameConfig.put("power_up_chance", 0.25);
        gameConfig.put("bonus_time_amount", 15);

        // Levels
        ArrayNode levels = MAPPER.createArrayNode();
        for (int i = 1; i <= 5; i++) {
            ObjectNode level = MAPPER.createObjectNode();
            level.put("id", i);
            level.put("stage_name", "Nivel " + i);
            level.put("pair_count", 2 + i);
            level.put("power_up_chance", 0.3);
            level.put("weight_life", 1.0);
            level.put("weight_time", 1.0);
            level.put("weight_hint", 1.0);
            levels.add(level);
        }
        gameConfig.set("levels", levels);
        root.set("game_config", gameConfig);

        /* ================= BRANDING ================= */
        ObjectNode branding = MAPPER.createObjectNode();

        // Images
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_logo_url", "https://cdn-icons-png.flaticon.com/512/3242/3242257.png");
        images.put("main_logo_offset_y", 60.0);
        images.put("logo_watermark_url", "https://cdn-icons-png.flaticon.com/512/1998/1998087.png");
        images.put("logo_watermark_offset_y", 0.0);
        images.put("card_back_url", "https://www.pngmart.com/files/22/Playing-Card-Back-PNG-File.png");
        images.put("icon_life_url", "https://cdn-icons-png.flaticon.com/512/833/833472.png");
        images.put("icon_hint_url", "https://cdn-icons-png.flaticon.com/512/2965/2965279.png");
        images.put("icon_time_url", "https://cdn-icons-png.flaticon.com/512/2838/2838590.png");
        branding.set("images", images);

        // Background Config
        ObjectNode backgroundConfig = MAPPER.createObjectNode();
        ObjectNode front = MAPPER.createObjectNode();
        front.put("SpriteUrl", "");
        front.put("ColorHex", "#FFFFFF80");
        front.put("Enabled", false);
        front.put("Speed", 1.0);
        front.put("Rotation", 0);
        front.put("LayoutMode", "TiledSquare");
        front.put("AspectRatio", 1.0);
        backgroundConfig.set("Front", front);

        ObjectNode back = MAPPER.createObjectNode();
        back.put("SpriteUrl", "https://img.freepik.com/free-vector/gradient-colorful-background_23-2149325375.jpg");
        back.put("ColorHex", "#FFFFFFFF");
        back.put("Enabled", true);
        back.put("Speed", 0.2);
        back.put("Rotation", 0);
        back.put("LayoutMode", "Stretched");
        back.put("AspectRatio", 1.77);
        backgroundConfig.set("Back", back);

        branding.set("background_config", backgroundConfig);
        root.set("branding", branding);

        /* ================= GAME ================= */
        ObjectNode game = MAPPER.createObjectNode();
        
        // Card Images
        ArrayNode cardImages = MAPPER.createArrayNode();
        String[] imageUrls = {
            "https://cdn-icons-png.flaticon.com/512/3468/3468377.png",  // Runner
            "https://cdn-icons-png.flaticon.com/512/2965/2965279.png",  // Bulb
            "https://cdn-icons-png.flaticon.com/512/3022/3022433.png",  // Coin
            "https://cdn-icons-png.flaticon.com/512/833/833472.png",    // Heart
            "https://cdn-icons-png.flaticon.com/512/2838/2838590.png",  // Clock
            "https://cdn-icons-png.flaticon.com/512/3004/3004458.png",  // Lightning
            "https://cdn-icons-png.flaticon.com/512/3050/3050526.png",  // Game
            "https://cdn-icons-png.flaticon.com/512/2620/2620656.png",  // Trophy
            "https://cdn-icons-png.flaticon.com/512/1998/1998087.png",  // Brain
            "https://cdn-icons-png.flaticon.com/512/3242/3242257.png"   // Cards
        };
        
        for (String url : imageUrls) {
            ObjectNode img = MAPPER.createObjectNode();
            img.put("url", url);
            cardImages.add(img);
        }
        game.set("card_images", cardImages);
        root.set("game", game);

        /* ================= AUDIO ================= */
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://cdn.pixabay.com/download/audio/2022/03/10/audio_c8c8e1c4c0.mp3");
        audio.put("flip_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_12b0c7443c.mp3");
        audio.put("match_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_0d0e1b1d9e.mp3");
        audio.put("mismatch_url", "https://cdn.pixabay.com/download/audio/2022/03/15/audio_688cfb3a52.mp3");
        audio.put("victory_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_0d0e1b1d9e.mp3");
        audio.put("defeat_url", "https://cdn.pixabay.com/download/audio/2022/03/15/audio_688cfb3a52.mp3");
        audio.put("powerup_url", "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3");
        root.set("audio", audio);

        /* ================= TEXTS ================= */
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡VICTORIA!");
        texts.put("victory_phrase", "¡Felicidades! ¡Completaste todos los niveles!");
        texts.put("defeat_title", "DERROTA");
        texts.put("defeat_phrase", "Inténtalo de nuevo");
        texts.put("label_time", "Tiempo");
        texts.put("label_attempts", "Intentos");
        texts.put("label_score", "Llaves");
        texts.put("label_level", "Nivel");
        root.set("texts", texts);

        /* ================= REWARDS ================= */
        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("coins_per_action", 2);
        rewards.put("coins_on_completion", 50);
        root.set("rewards", rewards);

        ASSETS = root;
    }

    private MemoryAssets() {}

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