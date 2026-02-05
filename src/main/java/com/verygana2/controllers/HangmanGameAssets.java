package com.verygana2.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class HangmanGameAssets {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final ObjectNode ASSETS;

    static {
        ASSETS = MAPPER.createObjectNode();

        // game_config
        ObjectNode gameConfig = ASSETS.putObject("game_config");
        gameConfig.put("time_limit", 60);
        gameConfig.put("difficulty", "normal");
        gameConfig.put("max_attempts", 3);

        // branding
        ObjectNode branding = ASSETS.putObject("branding");

        ObjectNode brandingImages = branding.putObject("images");
        brandingImages.put(
                "logo_watermark_url",
                "https://verygana.com/games/shared/assets/images/watermark.png"
        );

        ObjectNode brandingColors = branding.putObject("colors");
        brandingColors.put("primary", "#FF5500");
        brandingColors.put("secondary", "#FFFFFF");
        brandingColors.put("accent", "#1E1E1E");

        // game
        ObjectNode game = ASSETS.putObject("game");

        ObjectNode gameImages = game.putObject("images");
        gameImages.put(
                "main_image_url",
                "https://verygana.com/games/tile-puzzle/assets/images/image2.jpeg"
        );
        gameImages.put(
                "keyboard_sprite_url",
                "https://static.vecteezy.com/system/resources/thumbnails/014/324/180/small/round-shape-buttons-in-green-colors-user-interface-element-illustration-png.png"
        );

        // words
        ArrayNode words = game.putArray("words");

        words.addObject()
                .put("word", "BOY")
                .put("hint", "Young male")
                .put("score", 100);

        words.addObject()
                .put("word", "GIRL")
                .put("hint", "Young female")
                .put("score", 100);

        words.addObject()
                .put("word", "APPLE")
                .put("hint", "A red fruit")
                .put("score", 150);

        words.addObject()
                .put("word", "WATER")
                .put("hint", "You drink it")
                .put("score", 150);

        words.addObject()
                .put("word", "HELLO")
                .put("hint", "Greeting")
                .put("score", 150);

        // trivia_questions (vacío)
        game.set("trivia_questions", MAPPER.createObjectNode());

        // power_ups_config
        ArrayNode powerUps = game.putArray("power_ups_config");

        powerUps.addObject()
                .put("type", "RevealLetter")
                .put("display_name", "Pista")
                .put("color_hex", "#ffffffff")
                .put("cost", 50)
                .put("icon_url", "");

        powerUps.addObject()
                .put("type", "ZapOptions")
                .put("display_name", "Rayo")
                .put("color_hex", "#4ff61cff")
                .put("cost", 30)
                .put("icon_url", "");

        powerUps.addObject()
                .put("type", "ExtraLife")
                .put("display_name", "Vida")
                .put("color_hex", "#FF4B4B")
                .put("cost", 100)
                .put("icon_url", "");

        // hangman_progress_urls
        ArrayNode hangmanUrls = game.putArray("hangman_progress_urls");
        hangmanUrls.add("https://assets-abokamato.s3.us-east-2.amazonaws.com/hagman_1.png");
        hangmanUrls.add("https://assets-abokamato.s3.us-east-2.amazonaws.com/hagman_2.png");
        hangmanUrls.add("https://assets-abokamato.s3.us-east-2.amazonaws.com/hagman_3.png");
        hangmanUrls.add("https://assets-abokamato.s3.us-east-2.amazonaws.com/hagman_4.png");
        hangmanUrls.add("https://assets-abokamato.s3.us-east-2.amazonaws.com/hagman_5.png");
        hangmanUrls.add("https://assets-abokamato.s3.us-east-2.amazonaws.com/hagman_6.png");
        hangmanUrls.add("https://assets-abokamato.s3.us-east-2.amazonaws.com/hagman_7.png");

        game.putNull("menu_parallax_config");

        // audio
        ObjectNode audio = ASSETS.putObject("audio");
        audio.put("victory_sound_url", "");
        audio.put(
                "coin_sound_url",
                "https://verygana.com/games/tile-puzzle/assets/audio/coin.ogg"
        );

        // texts
        ObjectNode texts = ASSETS.putObject("texts");
        texts.put(
                "victory_phrase",
                "¡Felicidades, has ganado! Bancolombia te saluda ¡Estamos más cerca de ti!"
        );

        // rewards
        ObjectNode rewards = ASSETS.putObject("rewards");
        rewards.put("coins_per_action", 1);
        rewards.put("coins_on_completion", 20);
    }

    private HangmanGameAssets() {
    }
}
