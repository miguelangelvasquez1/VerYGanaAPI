package com.verygana2.services.interfaces;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.ad.responses.AdForConsumerDTO;
import com.verygana2.dtos.ad.responses.AdLikeResponseDTO;
import com.verygana2.dtos.ad.responses.AdLikedResponse;

public interface AdLikeService {

    AdLikedResponse processAdLike(UUID sessionId, Long adId, Long consumerId, String ipAddress);

    /**
     * Marca una sesión de visualización como completada (video visto hasta el umbral)
     * y solicita el XP correspondiente. Idempotente: si ya estaba WATCHED o LIKED,
     * no vuelve a otorgar XP.
     */
    void markWatchSessionCompleted(UUID sessionId, Long adId, Long consumerId);

    Optional<AdForConsumerDTO> getNextAdForConsumer(Long consumerId);

    boolean hasConsumerLikedAd(Long adId, Long consumerId);

    PagedResponse<AdLikeResponseDTO> getAdLikes(Long adId, Long commercialId, Pageable pageable);
}
