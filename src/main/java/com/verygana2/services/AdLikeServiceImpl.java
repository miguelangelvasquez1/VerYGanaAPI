package com.verygana2.services;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.exceptions.adsExceptions.AdNotFoundException;
import com.verygana2.exceptions.adsExceptions.DuplicateLikeException;
import com.verygana2.exceptions.adsExceptions.InvalidAdStateException;
import com.verygana2.models.Transaction;
import com.verygana2.models.User;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.ads.AdLike;
import com.verygana2.models.ads.AdLikeId;
import com.verygana2.models.enums.TransactionState;
import com.verygana2.models.enums.TransactionType;
import com.verygana2.repositories.AdLikeRepository;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.TransactionRepository;
import com.verygana2.repositories.UserRepository;
import com.verygana2.services.interfaces.AdLikeService;
import com.verygana2.services.interfaces.AdService;

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
    
    @Override
    public boolean processAdLike(
            Long adId, Long userId, String ipAddress) {
        
        log.info("Processing like for ad {} from user {}", adId, userId);
        
        // Verificar que el usuario existe
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new AdNotFoundException("Usuario no encontrado"));
        
        // Verificar que el anuncio existe
        Ad ad = adService.getAdEntityById(adId);
        
        // Verificar que el usuario no haya dado like antes
        if (hasUserLikedAd(adId, userId)) {
            throw new DuplicateLikeException("Ya has dado like a este anuncio");
        }
        
        // Verificar que el anuncio puede recibir likes
        if (!ad.canReceiveLike()) {
            throw new InvalidAdStateException(
                "Este anuncio no está disponible para recibir likes"
            );
        }
        
        // Crear el like
        AdLike adLike = AdLike.builder()
            .id(new AdLikeId(userId, adId))
            .user(user)
            .ad(ad)
            .rewardAmount(ad.getRewardPerLike())
            .createdAt(ZonedDateTime.now())
            .build();
        
        // Crear la transacción
        Transaction transaction = Transaction.builder()
            .wallet(user.getWallet())
            .amount(ad.getRewardPerLike())
            .transactionType(TransactionType.POINTS_AD_LIKE_REWARD)
            .transactionState(TransactionState.COMPLETED)
            .createdAt(ZonedDateTime.now())
            .completedAt(ZonedDateTime.now())
            .build();
        
        transaction = transactionRepository.save(transaction);
        
        // Guardar el like
        adLikeRepository.save(adLike);
        
        // Actualizar el anuncio
        ad.incrementLike(ad.getRewardPerLike());
        adRepository.save(ad);
        
        // Actualizar el balance del usuario
        user.getWallet().setBalance(user.getWallet().getBalance().add(ad.getRewardPerLike()));
        userRepository.save(user);
        
        log.info("Like processed successfully. User rewarded with: {}", 
            ad.getRewardPerLike());
        
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUserLikedAd(Long adId, Long userId) {
        return adLikeRepository.hasUserSeenAd(userId, adId);
    }
}