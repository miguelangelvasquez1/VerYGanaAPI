package com.verygana2.dtos.ad.responses;

import java.time.ZonedDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO para la respuesta de lista de me gustas de un anuncio
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdLikeResponseDTO {
    private Long userId;
    private String userName;     // nombre completo del usuario
    private ZonedDateTime likedAt;
}