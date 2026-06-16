package com.verygana2.services.pet;


import com.verygana2.dtos.pet.PetSessionResponseDTO;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.UnauthorizedException;
import com.verygana2.models.pets.PetSession;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.pet.PetSessionRepository;
import com.verygana2.services.interfaces.pet.PetSessionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class PetSessionServiceImpl implements PetSessionService {

    @Value("${games.session-expiration-minutes}")
    private Integer sessionExpirationMinutes;

    @Value("${pets.cdn-domain}")
    private String petsCdnDomain;

    @Value("${pets.game-path}")
    private String petGamePath;

    @PersistenceContext
    private EntityManager entityManager;

    private final PetSessionRepository petSessionRepository;

    @Override
    public PetSessionResponseDTO initSession(Long consumerId) {

        ConsumerDetails consumer =
                entityManager.getReference(ConsumerDetails.class, consumerId);

        PetSession session = PetSession.create(consumer);
        petSessionRepository.save(session);

        String url = String.format(
                "https://%s/%s?session_token=%s&user_hash=%s",
                petsCdnDomain,
                petGamePath,
                session.getSessionToken(),
                session.getUserHash()
        );

        return new PetSessionResponseDTO(url);
    }

    @Override
    public void validateSession(String sessionToken, String userHash) {

        PetSession session = petSessionRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new EntityNotFoundException("Pet session not found"));

        if (!session.getUserHash().equals(userHash)) {
            throw new UnauthorizedException("Session does not belong to user");
        }

        if (session.getStartTime()
                .plusMinutes(sessionExpirationMinutes)
                .isBefore(ZonedDateTime.now())) {
            throw new BusinessException("Pet session expired");
        }
    }
}