package com.verygana2.models.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

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

    public static SupportedMimeType fromValue(String valueRaw) {

        String value = normalizeMime(valueRaw);

        return Arrays.stream(values())
            .filter(v -> v.mime.equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() ->
                new ValidationException("MimeType no soportado: " + value)
            );
    }

    public static Set<SupportedMimeType> getSupportedMimeTypesForMediaType(MediaType mediaType) {
        return Arrays.stream(values())
            .filter(v -> v.mediaType == mediaType)
            .collect(java.util.stream.Collectors.toSet());
    }

    private static final Map<String, String> MIME_ALIASES = Map.ofEntries(
        Map.entry("image/x-png", "image/png"),
        Map.entry("image/jpg", "image/jpeg"),
        Map.entry("image/pjpeg", "image/jpeg"),
        Map.entry("image/x-jpeg", "image/jpeg"),
        Map.entry("audio/mp3", "audio/mpeg"),
        Map.entry("audio/x-mp3", "audio/mpeg"),
        Map.entry("audio/mpeg3", "audio/mpeg"),
        Map.entry("audio/x-mpeg-3", "audio/mpeg"),
        Map.entry("audio/x-ogg", "audio/ogg"),
        Map.entry("application/ogg", "audio/ogg"),
        Map.entry("audio/x-wav", "audio/wav"),
        Map.entry("audio/vnd.wave", "audio/wav"),
        Map.entry("video/x-mp4", "video/mp4"),
        Map.entry("video/x-quicktime", "video/quicktime")
    );

    private static String normalizeMime(String mime) {
        if (mime == null) return null;

        mime = mime.toLowerCase().trim();
        return MIME_ALIASES.getOrDefault(mime, mime);
    }
}
