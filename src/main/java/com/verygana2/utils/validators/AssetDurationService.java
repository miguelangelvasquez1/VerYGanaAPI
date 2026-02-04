package com.verygana2.utils.validators;

import java.util.Optional;

import org.springframework.stereotype.Service;
import com.github.kokorin.jaffree.LogLevel;
import com.github.kokorin.jaffree.ffprobe.FFprobe;
import com.github.kokorin.jaffree.ffprobe.Stream;
import com.github.kokorin.jaffree.ffprobe.Format;
import com.github.kokorin.jaffree.ffprobe.FFprobeResult;
import com.verygana2.exceptions.StorageException;
import com.verygana2.storage.service.R2Service;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssetDurationService {

    private final R2Service r2Service;

    private static final String PRIVATE_PREFIX = "private/";

    public Double getVideoDurationSeconds(String objectKey) {
        try {
            // Genera presigned URL con expiración corta (ffprobe es rápido)
            String presignedUrl = r2Service.generatePresignedUrl(PRIVATE_PREFIX + objectKey, 60);  // 60 segundos sobra

            FFprobeResult result = FFprobe.atPath()
                .setShowFormat(true)
                .setShowStreams(true)
                .setLogLevel(LogLevel.ERROR)
                .setInput(presignedUrl)  // ← ¡Aquí la magia!
                .execute();

            Float duration = Optional.ofNullable(result.getFormat())
                .map(Format::getDuration)
                .filter(d -> d != null && d > 0)
                .orElseGet(() -> result.getStreams().stream()
                    .filter(s -> "video".equals(s.getCodecType()))
                    .map(Stream::getDuration)
                    .filter(d -> d != null && d > 0)
                    .findFirst()
                    .orElse(null));

            if (duration == null || duration <= 0) {
                throw new ValidationException("Duración de video no detectable");
            }

            return Math.ceil(duration);

        } catch (Exception e) {
            log.error("Error obteniendo duración via presigned URL {}: {}", objectKey, e.getMessage(), e);
            throw new StorageException("Error obteniendo duración del video", e);
        }
    }
}