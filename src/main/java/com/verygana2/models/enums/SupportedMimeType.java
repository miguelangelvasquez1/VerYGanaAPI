package com.verygana2.models.enums;

import java.util.Arrays;

import jakarta.validation.ValidationException;

public enum SupportedMimeType {

    IMAGE_PNG("image/png", MediaType.IMAGE),
    IMAGE_JPEG("image/jpeg", MediaType.IMAGE),
    IMAGE_JPG("image/jpg", MediaType.IMAGE),
    IMAGE_WEBP("image/webp", MediaType.IMAGE),

    AUDIO_MPEG("audio/mpeg", MediaType.AUDIO),
    AUDIO_MP3("audio/mp3", MediaType.AUDIO),
    AUDIO_OGG("audio/ogg", MediaType.AUDIO),
    AUDIO_WAV("audio/wav", MediaType.AUDIO),

    VIDEO_MP4("video/mp4", MediaType.VIDEO),
    VIDEO_QUICK_TIME("video/quicktime", MediaType.VIDEO);

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
