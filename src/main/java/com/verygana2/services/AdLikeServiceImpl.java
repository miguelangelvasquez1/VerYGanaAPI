package com.verygana2.services;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.ad.responses.AdLikedResponse;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.adsExceptions.AdNotFoundException;
import com.verygana2.exceptions.adsExceptions.DuplicateLikeException;
import com.verygana2.exceptions.adsExceptions.InvalidAdStateException;
import com.verygana2.models.Transaction;
import com.verygana2.models.User;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.ads.AdLike;
import com.verygana2.models.ads.AdLikeId;
import com.verygana2.models.ads.AdWatchSession;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.AdWatchSessionStatus;
import com.verygana2.models.enums.TransactionState;
import com.verygana2.models.enums.TransactionType;
import com.verygana2.repositories.AdLikeRepository;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.AdWatchSessionRepository;
import com.verygana2.repositories.TransactionRepository;
import com.verygana2.repositories.UserRepository;
import com.verygana2.services.interfaces.AdLikeService;
import com.verygana2.services.interfaces.AdService;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdLikeServiceImpl implements AdLikeService {

    private final AdLikeRepository adLikeRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AdRepository adRepository;
    private final AdService adService;
    private final AdWatchSessionRepository adWatchSessionRepository;
    private final Clock clock;
    
    @Override
    @Transactional
    public AdLikedResponse processAdLike(UUID sessionId, Long adId, Long userId, String ipAddress) {
        
        log.info("Processing like for ad {} from user {}", adId, userId);
        
        // Verificar que el usuario existe
        Objects.requireNonNull(userId, "El ID de usuario no puede ser nulo");
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new AdNotFoundException("Usuario no encontrado"));
        
        // Verificar que el anuncio existe
        Ad ad = adService.getAdEntityById(adId);
        
        // 1. Verificar la sesión de visualización
        AdWatchSession session = validateSession(sessionId, userId, ad);

        // 2. Verificar que el usuario no haya dado like antes
        if (hasUserLikedAd(adId, userId)) {
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
            .id(new AdLikeId(userId, adId))
            .user(user)
            .ad(ad)
            .rewardAmount(ad.getRewardPerLike())
            .createdAt(ZonedDateTime.now())
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

        // 5. Crear la transacción
        Transaction transaction = Transaction.builder()
            .wallet(user.getWallet())
            .amount(ad.getRewardPerLike())
            .transactionType(TransactionType.POINTS_AD_LIKE_REWARD)
            .transactionState(TransactionState.COMPLETED)
            .createdAt(ZonedDateTime.now())
            .completedAt(ZonedDateTime.now())
            .build();
        
        transactionRepository.save(Objects.requireNonNull(transaction));
        
        // Actualizar el balance del usuario
        user.getWallet().setBalance(user.getWallet().getBalance().add(ad.getRewardPerLike()));
        userRepository.save(user);

        // 6. Actualizar la sesión de visualización
        session.setStatus(AdWatchSessionStatus.LIKED);
        adWatchSessionRepository.save(session);
        
        log.info("Like processed successfully. User rewarded with: {}", ad.getRewardPerLike());
        
        return new AdLikedResponse(true, ad.getRewardPerLike());
    }

    @Override
    public boolean hasUserLikedAd(Long adId, Long userId) {
        return adLikeRepository.hasUserSeenAd(userId, adId);
    }

    private AdWatchSession validateSession(UUID sessionId, Long userId, Ad ad) {

        AdWatchSession session = adWatchSessionRepository
            .findByIdAndUserIdAndAdId(sessionId, userId, ad.getId())
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
            ad.getDuration() != null ? ad.getDuration() : 0
        );

        Duration watched = Duration.between(session.getStartedAt(), now);

        if (watched.getSeconds() < requiredSeconds * 0.95) {
            throw new BusinessException("Anuncio no visto completamente");
        }

        return session;
    }
}