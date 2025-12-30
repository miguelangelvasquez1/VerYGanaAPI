package com.verygana2.models.enums;

import java.util.Arrays;

import jakarta.validation.ValidationException;

public enum SupportedMimeType {

    IMAGE_PNG("image/png", MediaType.IMAGE),
    IMAGE_JPEG("image/jpeg", MediaType.IMAGE),
    IMAGE_WEBP("image/webp", MediaType.IMAGE),

    AUDIO_MP3("audio/mpeg", MediaType.AUDIO),
    AUDIO_OGG("audio/ogg", MediaType.AUDIO),

    VIDEO_MP4("video/mp4", MediaType.VIDEO),

    APPLICATION_JSON("application/json", MediaType.TEXT);

    private final String mime;
    private final MediaType mediaType;

    SupportedMimeType(String mime, MediaType mediaType) {
        this.mime = mime;
        this.mediaType = mediaType;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public String getMime() {
        return mime;
    }

    public static SupportedMimeType fromValue(String value) {
        return Arrays.stream(values())
            .filter(v -> v.mime.equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() ->
                new ValidationException("MimeType no soportado: " + value)
            );
    }
}
