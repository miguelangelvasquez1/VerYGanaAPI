package com.verygana2.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class HangmanAssets {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final ObjectNode ASSETS;

    static {
        ObjectNode root = MAPPER.createObjectNode();

        /* ================= CONFIG ================= */
        ObjectNode config = MAPPER.createObjectNode();
        config.put("MaxErrors", 6);
        config.put("TimePerRound", 60);
        config.put("ColorBackgroundHex", "#FFFFFF");
        config.put("ColorKeyboardHex", "#E5E5E5");
        config.put("ColorTextKeyHex", "#ffffffff");
        config.put("MsgWin", "¡Felicidades,\nlo has conseguido!");
        config.put("MsgLose", "¡No te rindas!\nVas mejorando");
        config.put("MsgFinished", "PUNTUACIÓN: {0}");
        config.put("LogoUrl", "https://1000marcas.net/wp-content/uploads/2021/02/Duolingo-Logo.png");
        config.put("LogoOffsetY", 50);
        config.put("CompanyLogoUrl", "");
        config.put("BackgroundUrl", "https://assets-abokamato.s3.us-east-2.amazonaws.com/WhatsApp+Image+2026-02-02+at+11.03.45+AM.jpeg");
        config.put("KeyboardSpriteUrl", "https://static.vecteezy.com/system/resources/thumbnails/014/324/180/small/round-shape-buttons-in-green-colors-user-interface-element-illustration-png.png");
        config.putNull("MenuParallaxConfig");

        /* Hangman progress */
        ArrayNode progress = MAPPER.createArrayNode();
        progress.add("https://assets-abokamato.s3.us-east-2.amazonaws.com/hagman_1.png");
        progress.add("https://assets-abokamato.s3.us-east-2.amazonaws.com/hagman_2.png");
        progress.add("https://assets-abokamato.s3.us-east-2.amazonaws.com/hagman_3.png");
        progress.add("https://assets-abokamato.s3.us-east-2.amazonaws.com/hagman_4.png");
        progress.add("https://assets-abokamato.s3.us-east-2.amazonaws.com/hagman_5.png");
        progress.add("https://assets-abokamato.s3.us-east-2.amazonaws.com/hagman_6.png");
        progress.add("https://assets-abokamato.s3.us-east-2.amazonaws.com/hagman_7.png");
        config.set("HangmanProgressUrls", progress);

        /* PowerUps */
        ArrayNode powerUps = MAPPER.createArrayNode();

        powerUps.add(powerUp("RevealLetter", "Pista", "#ffffffff", "", 50));
        powerUps.add(powerUp("ZapOptions", "Rayo", "#4ff61cff", "", 30));
        powerUps.add(powerUp("ExtraLife", "Vida", "#FF4B4B", "", 100));

        config.set("PowerUps", powerUps);

        root.set("Config", config);

        /* ================= WORDS ================= */
        ArrayNode words = MAPPER.createArrayNode();
        words.add(word("BOY", "Young male", 100));
        words.add(word("GIRL", "Young female", 100));
        words.add(word("APPLE", "A red fruit", 150));
        words.add(word("WATER", "You drink it", 150));
        words.add(word("HELLO", "Greeting", 150));
        words.add(word("FAMILY", "Parents and kids", 200));
        words.add(word("MORNING", "Start of the day", 250));
        words.add(word("ENGLISH", "Language", 300));

        root.set("Words", words);

        ASSETS = root;
    }

    private static ObjectNode powerUp(String type, String name, String color, String icon, int cost) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("Type", type);
        p.put("DisplayName", name);
        p.put("ColorHex", color);
        p.put("IconUrl", icon);
        p.put("Cost", cost);
        return p;
    }

    private static ObjectNode word(String text, String hint, int score) {
        ObjectNode w = MAPPER.createObjectNode();
        w.put("Text", text);
        w.put("Hint", hint);
        w.put("Score", score);
        return w;
    }

    private HangmanAssets() {}
}
