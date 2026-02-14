package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class WhackAMoleAssets {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("meta", MAPPER.createObjectNode().put("brand_id", "WHACK_A_MOLE_DEFAULT"));

        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("duration", 60.0);
        gameConfig.put("gridRows", 3);
        gameConfig.put("gridCols", 3);
        gameConfig.put("spawnInterval", 0.8);
        gameConfig.put("moleLifetime", 2.0);
        gameConfig.put("pointsPerHit", 10);
        gameConfig.put("maxLives", 3);
        root.set("game_config", gameConfig);

        ObjectNode branding = MAPPER.createObjectNode();
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_image_url", "https://cdn-icons-png.flaticon.com/512/2917/2917337.png");
        images.put("logo_watermark_url", "https://cdn-icons-png.flaticon.com/512/3050/3050526.png");
        branding.set("images", images);

        ObjectNode shaderBg = MAPPER.createObjectNode();
        ObjectNode back = MAPPER.createObjectNode();
        back.put("SpriteUrl", "https://img.freepik.com/free-vector/green-grass-field-background_1308-48782.jpg");
        back.put("Enabled", true); back.put("ColorHex", "#FFFFFFFF");
        back.put("Speed", 0.1); back.put("Alpha", 1.0);
        ObjectNode tiling = MAPPER.createObjectNode(); tiling.put("x", 1); tiling.put("y", 1);
        back.set("Tiling", tiling);
        ObjectNode direction = MAPPER.createObjectNode(); direction.put("x", 1); direction.put("y", 0);
        back.set("Direction", direction);
        back.put("Rotation", 0);
        shaderBg.set("Back", back);
        shaderBg.set("Front", MAPPER.createObjectNode().put("Enabled", false));
        branding.set("shader_background_config", shaderBg);
        root.set("branding", branding);

        ObjectNode game = MAPPER.createObjectNode();
        game.put("holeSpriteUrl", "https://static.vecteezy.com/system/resources/previews/022/636/378/non_2x/brown-hole-in-the-ground-cartoon-vector.jpg");
        game.put("moleSpriteUrl", "https://cdn-icons-png.flaticon.com/512/2917/2917337.png");
        game.put("hitSpriteUrl", "https://cdn-icons-png.flaticon.com/512/616/616490.png");
        game.put("errorSpriteUrl", "");
        game.put("holeColorHex", "#333333");
        game.put("moleColorHex", "#8B4513");
        game.put("hitColorHex", "#FFFF00");
        game.put("errorColorHex", "#FF0000");
        game.put("showPreviewButtons", true);
        game.put("previewButtonSpriteUrl", "");
        game.put("previewButtonColorHex", "#FFFFFF");
        root.set("game", game);

        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://cdn.pixabay.com/download/audio/2022/03/10/audio_c8c8e1c4c0.mp3");
        audio.put("main_audio_url", "");
        audio.put("hit_sfx_url", "https://cdn.pixabay.com/download/audio/2022/03/24/audio_ce0e1c5fb5.mp3");
        audio.put("miss_sfx_url", "https://cdn.pixabay.com/download/audio/2022/03/15/audio_688cfb3a52.mp3");
        audio.put("win_url", "https://cdn.pixabay.com/download/audio/2021/08/04/audio_0d0e1b1d9e.mp3");
        audio.put("lose_url", "https://cdn.pixabay.com/download/audio/2022/03/15/audio_688cfb3a52.mp3");
        root.set("audio", audio);

        ASSETS = root;
    }

    private WhackAMoleAssets() {}
    public static ObjectNode getAssets() { return ASSETS; }
    public static String getAssetsAsString() {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) { return ASSETS.toString(); }
    }
}