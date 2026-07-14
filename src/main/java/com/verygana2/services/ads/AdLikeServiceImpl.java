package com.verygana2.services.ads;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.verygana2.event.XpAwardRequestedEvent;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.models.enums.*;
import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.ad.responses.AdForConsumerDTO;
import com.verygana2.dtos.ad.responses.AdLikeResponseDTO;
import com.verygana2.dtos.ad.responses.AdLikedResponse;
import com.verygana2.exceptions.adsExceptions.AdNotFoundException;
import com.verygana2.exceptions.adsExceptions.DuplicateLikeException;
import com.verygana2.exceptions.adsExceptions.InvalidAdStateException;
import com.verygana2.exceptions.adsExceptions.LimitReachedException;
import com.verygana2.mappers.AdMapper;
import com.verygana2.models.Category;
import com.verygana2.models.User;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.ads.AdLike;
import com.verygana2.models.ads.AdLikeId;
import com.verygana2.models.ads.AdWatchSession;
import com.verygana2.models.finance.KeyTransaction;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.AdLikeRepository;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.AdWatchSessionRepository;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.services.finance.KeyWalletServiceImpl.RewardSplit;
import com.verygana2.services.interfaces.AdLikeService;
import com.verygana2.services.interfaces.AdService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.finance.KeyWalletService;
import com.verygana2.services.scoring.ScoringContext;
import com.verygana2.storage.service.R2Service;

import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdLikeServiceImpl implements AdLikeService {

    @Value("${financial.purchase-keys-percentage:75}")
    private Long PURCHASE_KEYS_PERCENTAGE;
    @Value("${financial.connectivity-keys-percentage:25}")
    private Long CONNECTIVITY_KEYS_PERCENTAGE;

    @Value("${ads.watch-session-expiration-minutes:5}")
    private long watchSessionExpirationMinutes;

    @Value("${ads.cooldown-minutes:60}")
    private long adCooldownMinutes;

    @Value("${financial.key-value-cents:1000}")
    private long keyValueCents;

    private final AdLikeRepository adLikeRepository;
    private final ConsumerDetailsService consumerDetailsService;
    private final KeyWalletRepository keyWalletRepository;
    private final KeyWalletService keyWalletService;
    private final KeyTransactionRepository keyTransactionRepository;
    private final AdRepository adRepository;
    private final AdService adService;
    private final AdWatchSessionRepository adWatchSessionRepository;
    private final ConsumerDetailsRepository consumerDetailsRepository;
    private final AdScoringConfig adScoringConfig;
    private final Clock clock;
    private final AdScorer adScorer;
    private final AdMapper adMapper;
    private final R2Service r2Service;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(noRollbackFor = {ValidationException.class, LimitReachedException.class})
    public AdLikedResponse processAdLike(UUID sessionId, Long adId, Long consumerId, String ipAddress) {

        log.info("Processing like for ad {} from consumer {} at IP {}", adId, consumerId, ipAddress);

        // Verificar que el consumidor existe
        ConsumerDetails consumer = consumerDetailsService.getConsumerById(consumerId);

        // Verificar que el anuncio existe
        Ad ad = adService.getAdEntityById(adId);

        // 1. Verificar la sesión de visualización
        AdWatchSession session = validateSession(sessionId, consumerId, ad);
        AdWatchSessionStatus statusBeforeLike = session.getStatus();

        // 2. Verificar que el consumidor no haya dado like antes
        if (hasConsumerLikedAd(adId, consumerId)) {
            throw new DuplicateLikeException("Ya has dado like a este anuncio");
        }

        // 3. Verificar que el anuncio puede recibir likes
        if (!ad.canReceiveLike()) {
            throw new InvalidAdStateException("Este anuncio no está disponible para recibir likes");
        }

        // 4. Validar límite diario de likes por usuario
        if (ad.getMaxLikesPerUserPerDay() != null) {
            ZonedDateTime now = ZonedDateTime.now(clock);
            ZonedDateTime todayStart = now.toLocalDate().atStartOfDay(now.getZone());
            long todayLikes = adWatchSessionRepository.countByConsumerAdAndStatusSince(
                    consumerId, adId, AdWatchSessionStatus.LIKED, todayStart);
            if (todayLikes >= ad.getMaxLikesPerUserPerDay()) {
                session.setStatus(AdWatchSessionStatus.INVALIDATED);
                adWatchSessionRepository.save(session);
                throw new LimitReachedException("Has alcanzado el límite diario de likes para este anuncio");
            }
        }

        Long rewardKeysCents = ad.getRewardPerLike();

        // 4. Crear el like
        AdLike adLike = AdLike.builder()
                .id(new AdLikeId(consumerId, adId))
                .consumer(consumer)
                .ad(ad)
                .rewardAmount(rewardKeysCents)
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

            if (!ad.canReceiveLike()) {
                ad.setEndDate(ZonedDateTime.now(clock));
                ad.setStatus(AdStatus.COMPLETED);
            }

            adRepository.save(ad);
        } catch (OptimisticLockException e) {
            throw new ValidationException("El anuncio fue actualizado, intente nuevamente");
        }

        KeyWallet keyWallet = consumer.getKeyWallet();
        creditRewardToUser(keyWallet, rewardKeysCents, adId, sessionId);

        // 6. Actualizar la sesión de visualización
        session.setStatus(AdWatchSessionStatus.LIKED);
        adWatchSessionRepository.save(session);

        // Si markWatchSessionCompleted no fue llamado antes (sesión aún ACTIVE),
        // el XP de VIDEO_WATCHED nunca fue otorgado — publicarlo aquí.
        if (statusBeforeLike != AdWatchSessionStatus.WATCHED) {
            eventPublisher.publishEvent(
                    new XpAwardRequestedEvent(this, consumerId, ActivityType.VIDEO_WATCHED));
        }

        log.info("Like processed successfully. User rewarded with: {}", rewardKeysCents);

        return new AdLikedResponse(true, rewardKeysCents / keyValueCents);
    }


    @Override
    public void markWatchSessionCompleted(UUID sessionId, Long adId, Long consumerId) {
        log.info("Marking watch session {} as completed for consumer {} on ad {}",
                sessionId, consumerId, adId);

        // Pre-check de idempotencia para no romper en validateSession() cuando
        // la sesión ya pasó por WATCHED o LIKED en una llamada previa.
        AdWatchSession preCheck = adWatchSessionRepository
                .findByIdAndConsumerIdAndAdId(sessionId, consumerId, adId)
                .orElseThrow(() -> new BusinessException("Sesión de visualización inválida"));

        if (preCheck.getStatus() == AdWatchSessionStatus.WATCHED
                || preCheck.getStatus() == AdWatchSessionStatus.LIKED) {
            log.info("Session {} already marked as {} — skipping XP award",
                    sessionId, preCheck.getStatus());
            return;
        }

        Ad ad = adService.getAdEntityById(adId);
        AdWatchSession session = validateSession(sessionId, consumerId, ad);

        session.setStatus(AdWatchSessionStatus.WATCHED);
        adWatchSessionRepository.save(session);

        eventPublisher.publishEvent(
                new XpAwardRequestedEvent(this, consumerId, ActivityType.VIDEO_WATCHED));
    }

    @Override
    @Transactional(noRollbackFor = ValidationException.class)
    public Optional<AdForConsumerDTO> getNextAdForConsumer(Long consumerId) {

        log.debug("Buscando siguiente anuncio disponible para usuario: {}", consumerId);
        Objects.requireNonNull(consumerId, "El ID de usuario no puede ser nulo");

        ConsumerDetails consumer = consumerDetailsRepository.findById(consumerId).orElseThrow(
                () -> new ObjectNotFoundException("Consumer with id: " + consumerId + " not found ", User.class));

        ZonedDateTime now = ZonedDateTime.now(clock);

        // 1. Solo si hay sesion activa, se intenta reanudar el mismo anuncio.
        Optional<AdForConsumerDTO> resumed = resumeActiveSession(consumerId, now);
        if (resumed.isPresent()) return resumed;

        // 2. Hard filter (atributos obligatorios)
        List<Ad> candidates = findEligibleCandidates(consumer, now);
        if (candidates.isEmpty()) return Optional.empty();

        // 3. Pipeline de scoring + selección del mejor + creación de sesión de visualización
        return selectAndCreateSession(candidates, consumer, now);
    }

    private Optional<AdForConsumerDTO> resumeActiveSession(Long consumerId, ZonedDateTime now) {
        Optional<AdWatchSession> activeSession = adWatchSessionRepository
                .findFirstByConsumerIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(consumerId, AdWatchSessionStatus.ACTIVE, now);

        if (activeSession.isEmpty()) return Optional.empty();

        AdWatchSession session = activeSession.get();

        if (session.getResumeCount() >= 3) {
            session.setStatus(AdWatchSessionStatus.INVALIDATED);
            session.setExpiresAt(now);
            adWatchSessionRepository.save(session);
            log.info("AdWatchSession {} invalidated due to too many resumes", session.getId());
            throw new ValidationException("No se pudo reanudar la sesión de visualización. Has alcanzado el límite de reanudaciones permitidas para este anuncio.");
        }

        session.setResumeCount(Optional.ofNullable(session.getResumeCount()).orElse(0) + 1);
        session.setExpiresAt(now.plusSeconds(
                session.getAd().getAsset().getDurationSeconds() + watchSessionExpirationMinutes * 60));
        adWatchSessionRepository.save(session);

        return Optional.of(buildAdDto(session.getAd(), session.getId()));
    }

    private List<Ad> findEligibleCandidates(ConsumerDetails consumer, ZonedDateTime now) {
        ZonedDateTime cooldownThreshold = now.minusMinutes(adCooldownMinutes);
        ZonedDateTime todayStart = now.toLocalDate().atStartOfDay(now.getZone());

        List<Ad> candidates = adRepository.findEligibleAdsForConsumer(
                consumer.getId(),
                AdStatus.ACTIVE,
                List.of(AdWatchSessionStatus.LIKED),
                AdWatchSessionStatus.LIKED,
                now,
                consumer.getMunicipality(),
                todayStart,
                cooldownThreshold,
                AdWatchSessionStatus.ACTIVE,
                PageRequest.of(0, adScoringConfig.getCandidateLimit()));

        if (candidates.isEmpty()) log.debug("No hay anuncios elegibles para consumer {}", consumer.getId());
        return candidates;
    }

    private Optional<AdForConsumerDTO> selectAndCreateSession(List<Ad> candidates, ConsumerDetails consumer, ZonedDateTime now) {
        Set<Long> candidateIds = candidates.stream().map(Ad::getId).collect(Collectors.toSet());
        Map<Long, ZonedDateTime> lastViewedAt = adWatchSessionRepository
                .findLastViewedAtByAdIds(consumer.getId(), candidateIds)
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (ZonedDateTime) row[1]));

        ScoringContext ctx = new ScoringContext(
                consumer.getId(),
                consumer.getAge(),
                toTargetGender(consumer.getGender()),
                consumer.getCategories().stream().map(Category::getId).collect(Collectors.toSet()),
                lastViewedAt,
                now);

        return adScorer.selectBest(candidates, ctx).map(ad -> {
            AdWatchSession session = new AdWatchSession(consumer, ad);
            session.setStartedAt(now);
            session.setExpiresAt(now.plusSeconds(ad.getAsset().getDurationSeconds() + watchSessionExpirationMinutes * 60));
            adWatchSessionRepository.save(session);
            return buildAdDto(ad, session.getId());
        });
    }

    private AdForConsumerDTO buildAdDto(Ad ad, UUID sessionId) {
        AdForConsumerDTO dto = adMapper.toConsumerDto(ad);
        dto.setContentUrl(r2Service.buildPublicUrl(ad.getAsset().getObjectKey()));
        dto.setSessionUUID(sessionId);
        return dto;
    }

    @Override
    public boolean hasConsumerLikedAd(Long adId, Long consumerId) {
        return adLikeRepository.hasUserSeenAd(consumerId, adId);
    }

    @Override
    public PagedResponse<AdLikeResponseDTO> getAdLikes(Long adId, Long commercialId, Pageable pageable) {
        // Verificar que el anuncio pertenece al comercial
        adRepository.findByIdAndCommercialId(adId, commercialId)
                .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));

        Page<AdLikeResponseDTO> adLikes = adLikeRepository
                .findByAdIdOrderByCreatedAtDesc(adId, pageable)
                .map(like -> AdLikeResponseDTO.builder()
                        .userId(like.getConsumer().getId())
                        .userName(like.getConsumer().getName() + " " + like.getConsumer().getLastName())
                        .likedAt(like.getCreatedAt())
                        .build()
                );
        return PagedResponse.from(adLikes);
    }

    private AdWatchSession validateSession(UUID sessionId, Long consumerId, Ad ad) {

        AdWatchSession session = adWatchSessionRepository
                .findByIdAndConsumerIdAndAdId(sessionId, consumerId, ad.getId())
                .orElseThrow(() -> new ValidationException("Sesión de visualización inválida"));

        ZonedDateTime now = ZonedDateTime.now(clock);

        if (ad.getStatus() != AdStatus.ACTIVE) {
            session.setStatus(AdWatchSessionStatus.INVALIDATED);
            adWatchSessionRepository.save(session);
            throw new ValidationException("El anuncio ya no está activo");
        }

        // ACTIVE: mirando / WATCHED: completado, aún puede dar like.
        if (session.getStatus() != AdWatchSessionStatus.ACTIVE
                && session.getStatus() != AdWatchSessionStatus.WATCHED) {
            throw new ValidationException("La sesión no está activa");
        }

        // 2. Validar expiración de sesión
        if (now.isAfter(session.getExpiresAt())) {
            log.info("Session expired for session ID: {}, {}, {}, {}", sessionId, now, session.getExpiresAt(), session.getStartedAt());
            session.setStatus(AdWatchSessionStatus.EXPIRED);
            adWatchSessionRepository.save(session);
            throw new ValidationException("Sesión de visualización expirada");
        }

        // 3. Validar tiempo mínimo visto (ad.duration manda)
        long requiredSeconds = Math.round(
                ad.getAsset().getDurationSeconds() != null ? ad.getAsset().getDurationSeconds() : 0
        );

        double watchedSeconds = Duration.between(session.getStartedAt(), now).toMillis() / 1000.0;

        if (watchedSeconds < requiredSeconds * 0.95) {
            throw new ValidationException("Anuncio no visto completamente");
        }

        return session;
    }

    private void creditRewardToUser(KeyWallet keyWallet, Long rewardKeysCents, Long adId, UUID sessionId) {

        RewardSplit rewardSplit = keyWalletService.calculate(rewardKeysCents);

        ZonedDateTime purchaseExpiry = keyWalletService.calculatePurchaseExpiry();
        ZonedDateTime connectivityExpiry = keyWalletService.calculateConnectivityExpiry();

        String reason = "Interacción con anuncio #" + adId;
        keyTransactionRepository.save(Objects.requireNonNull(
                KeyTransaction.forInteractionPurchaseKeys(keyWallet, rewardSplit.purchaseKeysReward(), reason, sessionId, purchaseExpiry)));
        keyTransactionRepository.save(Objects.requireNonNull(
                KeyTransaction.forInteractionConnectivityKeys(keyWallet, rewardSplit.connectivityKeysReward(), reason, sessionId, connectivityExpiry)));

        keyWallet.creditKeysCents(rewardSplit.purchaseKeysReward(), rewardSplit.connectivityKeysReward());
        keyWalletRepository.save(keyWallet);
    }

    private TargetGender toTargetGender(Gender gender) {
        if (gender == Gender.MALE) return TargetGender.MALE;
        if (gender == Gender.FEMALE) return TargetGender.FEMALE;
        return null;
    }
}