package com.verygana2.dtos.recognitions;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.models.enums.UserLevel;

/**
 * Un usuario que el admin puede reconocer, con la métrica del criterio pedido. Sin correo ni
 * teléfono: para el detalle está GET /admin/users/consumers/{publicId}.
 *
 * @param metricValue llaves (KEYS_EARNED), compras (PURCHASES_COUNT), centavos
 *                    (PURCHASES_AMOUNT), partidas (GAMES_PLAYED) o XP
 */
public record RecognitionCandidateDTO(
        UUID publicId,
        String fullName,
        long metricValue,
        UserLevel level,
        LocalDateTime lastActivityAt,
        long recognitionsReceived,
        ZonedDateTime lastRecognitionAt) {
}
