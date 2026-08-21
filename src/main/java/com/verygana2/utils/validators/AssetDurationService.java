package com.verygana2.utils.validators;

import java.util.Iterator;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

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
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssetDurationService {

    private final R2Service r2Service;

    public Double getVideoDurationSeconds(String objectKey) {
        try {
            // Genera presigned URL con expiración corta (ffprobe es rápido)
            String presignedUrl = r2Service.getPrivateObject(objectKey, 60);  // 60 segundos sobra

            FFprobeResult result = FFprobe.atPath()
                .setShowFormat(true)
                .setShowStreams(true)
                .setLogLevel(LogLevel.ERROR)
                .setInput(presignedUrl)
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
            log.error("Error obteniendo duración via presigned URL: {}: {}", objectKey, e.getMessage(), e);
            throw new StorageException("Error obteniendo duración del video", e);
        }
    }

    public ImageDimensions getImageDimensions(String objectKey) {
        try (ResponseInputStream<GetObjectResponse> objectStream = r2Service.getPrivateObjectStream(objectKey);
                ImageInputStream imageInputStream = ImageIO.createImageInputStream(objectStream)) {

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                throw new ValidationException("No se pudieron leer las dimensiones de la imagen");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInputStream);
                return new ImageDimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error obteniendo dimensiones de la imagen: {}: {}", objectKey, e.getMessage(), e);
            throw new StorageException("Error obteniendo dimensiones de la imagen", e);
        }
    }

    public record ImageDimensions(int width, int height) {
    }
}