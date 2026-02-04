package com.verygana2.models.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum AssetType {
    
    LOGO("branding", "logo_watermark_url"),
    BANNER("branding", "banner_url"),
    ICON("branding", "icon_url"),
    THUMBNAIL("branding", "thumbnail_url"),
    BACKGROUND("branding", "logo_watermark_url"),
    SPRITE("branding", "sprite_url"),
    SOUNDTRACK("audio", "soundtrack_url"),
    SOUND_EFFECT("audio", "sound_effect_url");

    private final String blockKey; // bloque al que pertenece (branding, audio, puzzle)
    private final String jsonKey; // llave dentro de cada bloque (logo_url, image_url)

    public String getJsonKey() {
        return jsonKey;
    }

    public String getBlockKey() {
        return blockKey;
    }
}
