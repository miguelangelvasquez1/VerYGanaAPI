package com.verygana2.models.enums;

public enum MediaType {
    IMAGE("image"),
    AUDIO("audio"),
    MODEL("model"),
    VIDEO("video");

    private MediaType(String value) {
        this.value = value;
    }

    private final String value;

    public String getValue() {
        return value;
    }

    public static MediaType fromMimeType(String mimeType) {
        if (mimeType == null) {
            throw new IllegalArgumentException("Mime type cannot be null");
        }
        
        if (mimeType.startsWith("image/")) {
            return IMAGE;
        } else if (mimeType.startsWith("audio/")) {
            return AUDIO;
        } else if (mimeType.startsWith("video/")) {
            return VIDEO;
        } else if (mimeType.contains("model") || mimeType.contains("gltf") || mimeType.contains("glb")) {
            return MODEL;
        }
        
        throw new IllegalArgumentException("Unsupported mime type: " + mimeType);
    }
}
