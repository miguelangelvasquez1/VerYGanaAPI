// RaffleImagePolicy.java
package com.verygana2.models.enums.raffles;

import com.verygana2.models.enums.SupportedMimeType;
import java.util.Set;

public class RaffleImagePolicy {

    // Máximo 5 MB para imágenes de rifa y premios
    public static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024L;

    public static final Set<SupportedMimeType> ALLOWED_IMAGE_MIME_TYPES = Set.of(
        SupportedMimeType.IMAGE_JPEG,
        SupportedMimeType.IMAGE_PNG,
        SupportedMimeType.IMAGE_WEBP
    );

    private RaffleImagePolicy() {}
}