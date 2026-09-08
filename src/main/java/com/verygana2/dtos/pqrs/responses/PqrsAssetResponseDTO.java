package com.verygana2.dtos.pqrs.responses;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.MediaType;
import com.verygana2.models.enums.SupportedMimeType;

import lombok.Data;

@Data
public class PqrsAssetResponseDTO {
    private Long id;
    private String originalFileName;
    private MediaType mediaType;
    private SupportedMimeType mimeType;
    private Long sizeBytes;
    private ZonedDateTime createdAt;

    /** URL absoluta propia del backend ("{appBaseUrl}/pqrs/assets/{id}/view"), no una URL firmada externa — la arma el mapper. Requiere JWT (header o ?token=). */
    private String viewUrl;
}
