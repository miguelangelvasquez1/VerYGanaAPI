package com.verygana2.services;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.ad.responses.AdLikedResponse;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.adsExceptions.DuplicateLikeException;
import com.verygana2.exceptions.adsExceptions.InvalidAdStateException;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.ads.AdLike;
import com.verygana2.models.ads.AdLikeId;
import com.verygana2.models.ads.AdWatchSession;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.AdWatchSessionStatus;
import com.verygana2.models.finance.KeyTransaction;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.AdLikeRepository;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.AdWatchSessionRepository;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.services.interfaces.AdLikeService;
import com.verygana2.services.interfaces.AdService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdLikeServiceImpl implements AdLikeService {

    private static final Long PURCHASE_KEYS_PERCENTAGE = 75L;
    private static final Long CONNECTIVITY_KEYS_PERCENTAGE = 25L;

    private final AdLikeRepository adLikeRepository;
    private final ConsumerDetailsService consumerDetailsService;
    private final KeyWalletRepository keyWalletRepository;
    private final KeyTransactionRepository keyTransactionRepository;
    private final AdRepository adRepository;
    private final AdService adService;
    private final AdWatchSessionRepository adWatchSessionRepository;
    private final Clock clock;
    
    @Override
    @Transactional
    public AdLikedResponse processAdLike(UUID sessionId, Long adId, Long consumerId, String ipAddress) {
        
        log.info("Processing like for ad {} from consumer {}", adId, consumerId);
        
        // Verificar que el consumidor existe
        ConsumerDetails consumer = consumerDetailsService.getConsumerById(consumerId);
        
        // Verificar que el anuncio existe
        Ad ad = adService.getAdEntityById(adId);
        
        // 1. Verificar la sesión de visualización
        AdWatchSession session = validateSession(sessionId, consumerId, ad);

        // 2. Verificar que el consumidor no haya dado like antes
        if (hasConsumerLikedAd(adId, consumerId)) {
            throw new DuplicateLikeException("Ya has dado like a este anuncio");
        }
        
        // 3. Verificar que el anuncio puede recibir likes
        if (!ad.canReceiveLike()) {
            throw new InvalidAdStateException(
                "Este anuncio no está disponible para recibir likes"
            );
        }
        
        // 4. Crear el like
        AdLike adLike = AdLike.builder()
            .id(new AdLikeId(consumerId, adId))
            .consumer(consumer)
            .ad(ad)
            .rewardAmount(ad.getRewardPerLike())
            .createdAt(ZonedDateTime.now(clock))
            .build();

        try {
            adLikeRepository.save(Objects.requireNonNull(adLike));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateLikeException("Like ya procesado previamente");
        }

        // Actualizar el anuncio
        try {
            ad.incrementLike();
            adRepository.save(ad);
        } catch (OptimisticLockException e) {
            throw new BusinessException("El anuncio fue actualizado, intente nuevamente");
        }

        KeyWallet keyWallet = consumer.getKeyWallet();
        long purchaseKeysReward = (ad.getRewardPerLike() * PURCHASE_KEYS_PERCENTAGE) / 100;
        long connectivityKeysReward = (ad.getRewardPerLike() * CONNECTIVITY_KEYS_PERCENTAGE) / 100;

        ZoneId colombia = ZoneId.of("America/Bogota");
        ZonedDateTime nowColombia = ZonedDateTime.now(clock).withZoneSameInstant(colombia);
        ZonedDateTime purchaseExpiry = nowColombia.toLocalDate()
            .withDayOfMonth(1).plusMonths(1)
            .atStartOfDay(colombia)
            .withZoneSameInstant(ZoneOffset.UTC);
        ZonedDateTime connectivityExpiry = nowColombia.toLocalDate()
            .plusDays(1)
            .atStartOfDay(colombia)
            .withZoneSameInstant(ZoneOffset.UTC);

        String reason = "Interaction with ad: #" + adId;
        keyTransactionRepository.save(Objects.requireNonNull(
            KeyTransaction.forInteractionPurchaseKeys(keyWallet, purchaseKeysReward, reason, sessionId, purchaseExpiry)));
        keyTransactionRepository.save(Objects.requireNonNull(
            KeyTransaction.forInteractionConnectivityKeys(keyWallet, connectivityKeysReward, reason, sessionId, connectivityExpiry)));

        keyWallet.creditKeys(purchaseKeysReward, connectivityKeysReward);
        keyWalletRepository.save(keyWallet);

        // 6. Actualizar la sesión de visualización
        session.setStatus(AdWatchSessionStatus.LIKED);
        adWatchSessionRepository.save(session);
        
        log.info("Like processed successfully. User rewarded with: {}", ad.getRewardPerLike());
        
        return new AdLikedResponse(true, ad.getRewardPerLike());
    }

    @Override
    public boolean hasConsumerLikedAd(Long adId, Long consumerId) {
        return adLikeRepository.hasUserSeenAd(consumerId, adId);
    }

    private AdWatchSession validateSession(UUID sessionId, Long consumerId, Ad ad) {

        AdWatchSession session = adWatchSessionRepository
            .findByIdAndConsumerIdAndAdId(sessionId, consumerId, ad.getId())
            .orElseThrow(() -> new BusinessException("Sesión de visualización inválida"));

        ZonedDateTime now = ZonedDateTime.now(clock);

        if (ad.getStatus() != AdStatus.ACTIVE) {
            session.setStatus(AdWatchSessionStatus.INVALIDATED);
            throw new BusinessException("El anuncio ya no está activo");
        }

        if (session.getStatus() != AdWatchSessionStatus.ACTIVE) {
            throw new BusinessException("La sesión no está activa");
        }

        // 2. Validar expiración de sesión
        if (now.isAfter(session.getExpiresAt())) {
            log.info("Session expired for session ID: {}, {}, {}, {}", sessionId, now, session.getExpiresAt(), session.getStartedAt());
            session.setStatus(AdWatchSessionStatus.EXPIRED);
            adWatchSessionRepository.save(session);
            throw new BusinessException("Sesión de visualización expirada");
        }

        // 3. Validar tiempo mínimo visto (ad.duration manda)
        long requiredSeconds = Math.round(
            ad.getAsset().getDurationSeconds() != null ? ad.getAsset().getDurationSeconds() : 0
        );

        Duration watched = Duration.between(session.getStartedAt(), now);

        if (watched.getSeconds() < requiredSeconds * 0.95) {
            throw new BusinessException("Anuncio no visto completamente");
        }

        return session;
    }
}