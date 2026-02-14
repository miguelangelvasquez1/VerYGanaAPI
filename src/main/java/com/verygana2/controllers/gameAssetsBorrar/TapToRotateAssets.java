package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Configuración completa del juego Tap To Rotate (Runner Endless)
 */
public final class TapToRotateAssets {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();

        /* ================= META ================= */
        ObjectNode meta = MAPPER.createObjectNode();
        meta.put("brand_id", "TAP_TO_ROTATE_DEFAULT");
        root.set("meta", meta);

        /* ================= GAME_CONFIG ================= */
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("scroll_speed", 5.0);
        gameConfig.put("max_scroll_speed", 15.0);
        gameConfig.put("acceleration", 0.5);
        gameConfig.put("jump_force", 10.0);
        gameConfig.put("gravity_scale", 1.0);
        gameConfig.put("game_duration", 120);
        gameConfig.put("use_countdown", true);
        gameConfig.put("max_lives", 3);
        root.set("game_config", gameConfig);

        /* ================= BRANDING ================= */
        ObjectNode branding = MAPPER.createObjectNode();

        // Images
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_logo_url", "https://cdn-icons-png.flaticon.com/512/3468/3468377.png");
        images.put("main_logo_offset_y", 80.0);
        images.put("logo_watermark_url", "https://cdn-icons-png.flaticon.com/512/2111/2111370.png");
        images.put("logo_watermark_offset_y", 0.0);
        branding.set("images", images);

        // Visuals
        ObjectNode visuals = MAPPER.createObjectNode();
        visuals.put("player_url", "https://www.pngall.com/wp-content/uploads/13/Geometry-Dash-PNG-Picture.png");
        visuals.put("ground_url", "https://opengameart.org/sites/default/files/ground_grass_gen_01.png");
        visuals.put("ground_trap_url", "https://cdn-icons-png.flaticon.com/512/2797/2797387.png");
        visuals.put("coin_url", "https://cdn-icons-png.flaticon.com/512/3022/3022433.png");
        visuals.put("air_trap_url", "https://cdn-icons-png.flaticon.com/512/562/562678.png");
        visuals.put("bg_image_url", "https://img.freepik.com/free-vector/blue-sky-background-video-conferencing_23-2148639325.jpg");
        visuals.put("death_wall_url", "https://static.vecteezy.com/system/resources/previews/009/383/461/original/brick-wall-clipart-design-illustration-free-png.png");
        visuals.put("coin_scale", 1.0);
        visuals.put("air_trap_scale", 1.2);
        visuals.put("ground_trap_scale", 1.0);
        visuals.put("ground_texture_scale", 2.0);
        visuals.put("bg_texture_scale", 1.5);
        visuals.put("bg_solid_color", "#87CEEB");
        visuals.put("ground_color", "#8B4513");
        visuals.put("dw_primary_color", "#8B0000");
        visuals.put("dw_secondary_color", "#DC143C");
        visuals.put("dw_bg_color", "#2F4F4F");
        visuals.put("dw_scroll_x", 0.5);
        visuals.put("dw_scroll_y", 0.0);
        visuals.put("dw_desphase_x", 0.2);
        visuals.put("dw_desphase_y", 0.1);
        branding.set("visuals", visuals);

        // Background Config
        ObjectNode backgroundConfig = MAPPER.createObjectNode();
        
        ObjectNode front = MAPPER.createObjectNode();
        front.put("SpriteUrl", "https://opengameart.org/sites/default/files/cloud_0.png");
        front.put("ColorHex", "#FFFFFFFF");
        front.put("Enabled", true);
        front.put("Speed", 2.0);
        front.put("Rotation", 0);
        front.put("LayoutMode", "TiledSquare");
        front.put("AspectRatio", 1.0);
        backgroundConfig.set("Front", front);

        ObjectNode back = MAPPER.createObjectNode();
        back.put("SpriteUrl", "https://opengameart.org/sites/default/files/mountain.png");
        back.put("ColorHex", "#FFFFFFFF");
        back.put("Enabled", true);
        back.put("Speed", 0.5);
        back.put("Rotation", 0);
        back.put("LayoutMode", "Stretched");
        back.put("AspectRatio", 1.77);
        backgroundConfig.set("Back", back);

        branding.set("background_config", backgroundConfig);
        root.set("branding", branding);

        /* ================= AUDIO ================= */
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://cdn.pixabay.com/download/audio/2022/05/27/audio_1808fbf07a.mp3");
        audio.put("jump_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_12b0c7443c.mp3");
        audio.put("land_url", "https://cdn.pixabay.com/download/audio/2022/03/15/audio_3e6d39c441.mp3");
        audio.put("coin_url", "https://cdn.pixabay.com/download/audio/2022/03/10/audio_c8c8e1c4c0.mp3");
        audio.put("death_url", "https://cdn.pixabay.com/download/audio/2022/03/15/audio_688cfb3a52.mp3");
        audio.put("victory_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_0d0e1b1d9e.mp3");
        audio.put("lose_url", "https://cdn.pixabay.com/download/audio/2022/03/15/audio_688cfb3a52.mp3");
        root.set("audio", audio);

        /* ================= TEXTS ================= */
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡VICTORIA!");
        texts.put("victory_phrase", "¡Increíble! ¡Lo lograste!");
        texts.put("defeat_title", "GAME OVER");
        texts.put("defeat_phrase", "¡Inténtalo de nuevo!");
        texts.put("label_score", "Llaves");
        texts.put("label_time", "Tiempo");
        texts.put("label_record", "Récord");
        root.set("texts", texts);

        ASSETS = root;
    }

    private TapToRotateAssets() {}

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