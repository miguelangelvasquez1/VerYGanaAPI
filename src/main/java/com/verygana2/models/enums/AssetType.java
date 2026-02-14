package com.verygana2.models.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum AssetType {
    
    // BRANDING ASSETS
    LOGO("branding", "main_image_url"),
    WATERMARK("branding", "logo_watermark_url"),
    BANNER("branding", "banner_url"),
    ICON("branding", "icon_url"),
    THUMBNAIL("branding", "thumbnail_url"),
    BACKGROUND("branding", "background_url"),
    PARALLAX_LAYER("branding", "parallax_layer_url"),
    
    // GAME ASSETS
    SPRITE("game", "sprite_url"),
    TEXTURE("game", "texture_url"),
    CARD_IMAGE("game", "card_image_url"),
    CARD_BACK("game", "card_back_url"),
    CARD_FRAME("game", "card_frame_url"),
    POWERUP_ICON("game", "powerup_icon_url"),
    HANGMAN_PROGRESS("game", "hangman_progress_url"),
    PUZZLE_PIECE("game", "puzzle_piece_url"),
    OBSTACLE("game", "obstacle_url"),
    COLLECTIBLE("game", "collectible_url"),
    
    // AUDIO ASSETS
    MUSIC("audio", "music_url"),
    SFX("audio", "sfx_url"),
    SOUNDTRACK("audio", "soundtrack_url"),
    SOUND_EFFECT("audio", "sound_effect_url");

    private final String blockKey; // bloque al que pertenece (branding, audio, puzzle)
    private final String jsonKey; // llave dentro de cada bloque (logo_url, image_url)
}
