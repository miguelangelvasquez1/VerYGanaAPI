package com.verygana2.controllers.gameAssetsBorrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Configuración completa del juego Hangman (Ahorcado)
 * Basado en la documentación JSON del juego
 */
public final class HangmanAssets {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();

        /* ================= META ================= */
        ObjectNode meta = MAPPER.createObjectNode();
        meta.put("brand_id", "HANGMAN_DEFAULT");
        root.set("meta", meta);

        /* ================= GAME_CONFIG ================= */
        ObjectNode gameConfig = MAPPER.createObjectNode();
        gameConfig.put("time_limit", 40);
        gameConfig.put("difficulty", "normal");
        gameConfig.put("max_attempts", 6);
        root.set("game_config", gameConfig);

        /* ================= BRANDING ================= */
        ObjectNode branding = MAPPER.createObjectNode();

        // Images
        ObjectNode images = MAPPER.createObjectNode();
        images.put("main_image_url", "https://images.unsplash.com/photo-1761839258753-85d8eecbbc29?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDF8MHxmZWF0dXJlZC1waG90b3MtZmVlZHwxfHx8ZW58MHx8fHx8");
        images.put("main_image_offset_y", 50);
        images.put("logo_watermark_url", "https://images.unsplash.com/photo-1761839258753-85d8eecbbc29?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDF8MHxmZWF0dXJlZC1waG90b3MtZmVlZHwxfHx8ZW58MHx8fHx8");
        images.put("logo_watermark_offset_y", 0);
        images.put("keyboard_sprite_url", "https://images.unsplash.com/photo-1761839258753-85d8eecbbc29?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDF8MHxmZWF0dXJlZC1waG90b3MtZmVlZHwxfHx8ZW58MHx8fHx8");
        branding.set("images", images);

        // Shader Background Config
        ObjectNode shaderBgConfig = MAPPER.createObjectNode();
        
        ObjectNode backLayer = MAPPER.createObjectNode();
        backLayer.put("SpriteUrl", "https://images.unsplash.com/photo-1761839258753-85d8eecbbc29?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDF8MHxmZWF0dXJlZC1waG90b3MtZmVlZHwxfHx8ZW58MHx8fHx8");
        backLayer.put("Enabled", true);
        backLayer.put("ColorHex", "#FFFFFFFF");
        backLayer.put("Speed", 0.1);
        backLayer.put("Alpha", 1.0);
        
        ObjectNode backTiling = MAPPER.createObjectNode();
        backTiling.put("x", 1);
        backTiling.put("y", 1);
        backLayer.set("Tiling", backTiling);
        
        ObjectNode backDirection = MAPPER.createObjectNode();
        backDirection.put("x", 1);
        backDirection.put("y", 0);
        backLayer.set("Direction", backDirection);
        backLayer.put("Rotation", 0);
        
        shaderBgConfig.set("Back", backLayer);

        ObjectNode frontLayer = MAPPER.createObjectNode();
        frontLayer.put("SpriteUrl", "");
        frontLayer.put("Enabled", false);
        frontLayer.put("ColorHex", "#FFFFFF00");
        frontLayer.put("Speed", 0.05);
        frontLayer.put("Alpha", 0.3);
        
        ObjectNode frontTiling = MAPPER.createObjectNode();
        frontTiling.put("x", 1);
        frontTiling.put("y", 1);
        frontLayer.set("Tiling", frontTiling);
        
        ObjectNode frontDirection = MAPPER.createObjectNode();
        frontDirection.put("x", 0);
        frontDirection.put("y", 1);
        frontLayer.set("Direction", frontDirection);
        frontLayer.put("Rotation", 0);
        
        shaderBgConfig.set("Front", frontLayer);
    branding.set("shader_background_config", shaderBgConfig);

        branding.putNull("parallax_config");

        root.set("branding", branding);

        /* ================= GAME ================= */
        ObjectNode game = MAPPER.createObjectNode();
        game.put("font_color_hex", "#2C3E50");

        // Words list
        ArrayNode words = MAPPER.createArrayNode();
        words.add(createWord("ADVENTURE", "Exciting journey or experience", 100));
        words.add(createWord("BUTTERFLY", "Colorful flying insect", 120));
        // words.add(createWord("CHOCOLATE", "Sweet brown treat", 120));
        // words.add(createWord("DIAMOND", "Precious gemstone", 100));
        // words.add(createWord("ELEPHANT", "Large grey animal with trunk", 120));
        // words.add(createWord("FREEDOM", "State of being free", 100));
        // words.add(createWord("GUITAR", "Musical string instrument", 100));
        // words.add(createWord("HAPPINESS", "Feeling of joy", 120));
        // words.add(createWord("ISLAND", "Land surrounded by water", 100));
        // words.add(createWord("JUNGLE", "Dense tropical forest", 100));
        // words.add(createWord("KITCHEN", "Room for cooking", 100));
        // words.add(createWord("LIBRARY", "Place with many books", 100));
        // words.add(createWord("MOUNTAIN", "Very high hill", 120));
        // words.add(createWord("NATURE", "The natural world", 100));
        // words.add(createWord("OCEAN", "Large body of salt water", 80));
        // words.add(createWord("PLANET", "Celestial body orbiting a star", 100));
        // words.add(createWord("RAINBOW", "Colorful arc in the sky", 100));
        // words.add(createWord("SUNRISE", "Dawn, start of the day", 100));
        // words.add(createWord("TREASURE", "Hidden valuable items", 120));
        // words.add(createWord("UMBRELLA", "Protection from rain", 120));
        game.set("words", words);

        // Power-ups config
        ArrayNode powerUpsConfig = MAPPER.createArrayNode();
        powerUpsConfig.add(createPowerUp(
            "RevealLetter",
            "Revelar Letra",
            "#3498DBFF",
            50,
            "https://images.unsplash.com/photo-1761839258753-85d8eecbbc29?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDF8MHxmZWF0dXJlZC1waG90b3MtZmVlZHwxfHx8ZW58MHx8fHx8"
        ));
        powerUpsConfig.add(createPowerUp(
            "ZapOptions",
            "Eliminar Opciones",
            "#4ff61cff",
            30,
            "https://images.unsplash.com/photo-1761839258753-85d8eecbbc29?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDF8MHxmZWF0dXJlZC1waG90b3MtZmVlZHwxfHx8ZW58MHx8fHx8"
        ));
        powerUpsConfig.add(createPowerUp(
            "ExtraLife",
            "Vida Extra",
            "#E74C3CFF",
            100,
            "https://images.unsplash.com/photo-1761839258753-85d8eecbbc29?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDF8MHxmZWF0dXJlZC1waG90b3MtZmVlZHwxfHx8ZW58MHx8fHx8"
        ));
        game.set("power_ups_config", powerUpsConfig);

        // Hangman progress images
        ArrayNode hangmanProgressUrls = MAPPER.createArrayNode();
        hangmanProgressUrls.add("https://images.unsplash.com/photo-1761839258753-85d8eecbbc29?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDF8MHxmZWF0dXJlZC1waG90b3MtZmVlZHwxfHx8ZW58MHx8fHx8"); // Paso 1 - Base
        hangmanProgressUrls.add("https://plus.unsplash.com/premium_photo-1770052048194-323a3e8f656a?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxmZWF0dXJlZC1waG90b3MtZmVlZHw2fHx8ZW58MHx8fHx8"); // Paso 2 - Poste vertical
        hangmanProgressUrls.add("https://images.unsplash.com/photo-1761839258753-85d8eecbbc29?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDF8MHxmZWF0dXJlZC1waG90b3MtZmVlZHwxfHx8ZW58MHx8fHx8"); // Paso 3 - Poste horizontal
        hangmanProgressUrls.add("https://plus.unsplash.com/premium_photo-1770052048194-323a3e8f656a?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxmZWF0dXJlZC1waG90b3MtZmVlZHw2fHx8ZW58MHx8fHx8"); // Paso 4 - Cuerda
        hangmanProgressUrls.add("https://images.unsplash.com/photo-1761839258753-85d8eecbbc29?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDF8MHxmZWF0dXJlZC1waG90b3MtZmVlZHwxfHx8ZW58MHx8fHx8"); // Paso 5 - Cabeza
        hangmanProgressUrls.add("https://plus.unsplash.com/premium_photo-1770052048194-323a3e8f656a?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxmZWF0dXJlZC1waG90b3MtZmVlZHw2fHx8ZW58MHx8fHx8"); // Paso 6 - Cuerpo completo
        hangmanProgressUrls.add("https://images.unsplash.com/photo-1761839258753-85d8eecbbc29?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDF8MHxmZWF0dXJlZC1waG90b3MtZmVlZHwxfHx8ZW58MHx8fHx8"); // Paso 7 - Ahorcado completo (pierde)
        game.set("hangman_progress_urls", hangmanProgressUrls);

        root.set("game", game);

        /* ================= AUDIO ================= */
        ObjectNode audio = MAPPER.createObjectNode();
        audio.put("music_url", "https://games.verygana.com/asset_tests/alarm.mp3");
        audio.put("click_url", "https://games.verygana.com/asset_tests/item_select.wav");
        audio.put("victory_sound_url", "https://games.verygana.com/asset_tests/alarm.mp3");
        audio.put("defeat_sound_url", "https://games.verygana.com/asset_tests/alarm.mp3");
        audio.put("reveal_sound_url", "https://games.verygana.com/asset_tests/alarm.mp3");
        audio.put("zap_sound_url", "https://games.verygana.com/asset_tests/alarm.mp3");
        audio.put("life_sound_url", "https://games.verygana.com/asset_tests/alarm.mp3");
        root.set("audio", audio);

        /* ================= TEXTS ================= */
        ObjectNode texts = MAPPER.createObjectNode();
        texts.put("victory_title", "¡VICTORIA!");
        texts.put("victory_phrase", "¡Felicidades! este es un mensaje de prueba");
        texts.put("defeat_title", "GAME OVER");
        texts.put("defeat_phrase", "¡Inténtalo de nuevo! este es un lol");
        root.set("texts", texts);

        /* ================= REWARDS ================= */
        ObjectNode rewards = MAPPER.createObjectNode();
        rewards.put("coins_per_action", 5);
        rewards.put("coins_on_completion", 50);
        root.set("rewards", rewards);

        ASSETS = root;
    }

    /**
     * Crea un objeto de palabra con sus propiedades
     */
    private static ObjectNode createWord(String word, String hint, int score) {
        ObjectNode w = MAPPER.createObjectNode();
        w.put("word", word);
        w.put("hint", hint);
        w.put("score", score);
        return w;
    }

    /**
     * Crea un objeto de power-up con sus propiedades
     */
    private static ObjectNode createPowerUp(String type, String displayName, String colorHex, int cost, String iconUrl) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("type", type);
        p.put("display_name", displayName);
        p.put("color_hex", colorHex);
        p.put("cost", cost);
        p.put("icon_url", iconUrl);
        return p;
    }

    private HangmanAssets() {
        // Private constructor to prevent instantiation
    }

    /**
     * Obtiene la configuración completa del juego en formato JSON
     */
    public static ObjectNode getAssets() {
        return ASSETS;
    }

    /**
     * Obtiene la configuración completa como String JSON
     */
    public static String getAssetsAsString() {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ASSETS);
        } catch (Exception e) {
            return ASSETS.toString();
        }
    }
}