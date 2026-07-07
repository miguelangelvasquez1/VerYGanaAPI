package com.verygana2.services.compliance;

import java.time.LocalDateTime;
import java.util.List;

import com.verygana2.services.interfaces.compliance.ScreeningPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.exceptions.compliance.ScreeningHitException;
import com.verygana2.models.compliance.ScreeningResult;
import com.verygana2.models.enums.ScreeningStatus;
import com.verygana2.repositories.compliance.ScreeningResultRepository;
import com.verygana2.services.interfaces.compliance.ScreeningService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScreeningServiceImpl implements ScreeningService {

    private final ScreeningPort screeningPort;
    private final ScreeningResultRepository screeningResultRepository;

    @Override
    public void screenOrThrow(Long userId, String name, String queriedDocument) {
        List<ScreeningOutcome> outcomes;
        try {
            outcomes = screeningPort.screen(name, queriedDocument);
        } catch (Exception e) {
            // If the provider fails, we log but do not block registration (fail open)
            log.error("Screening provider failed for userId={} name='{}': {}", userId, name, e.getMessage());
            return;
        }

        List<ScreeningResult> results = outcomes.stream()
                .map(o -> ScreeningResult.builder()
                        .userId(userId)
                        .queriedName(name)
                        .queriedDocument(queriedDocument)
                        .restrictiveList(o.restrictiveList())
                        .status(o.status())
                        .referenceId(o.referenceId())
                        .rawResponse(o.rawResponse())
                        .build())
                .toList();

        // Persist in an independent TX so records survive if the outer TX rolls back
        persistResults(results);

        results.stream()
                .filter(r -> r.getStatus() == ScreeningStatus.HIT)
                .findFirst()
                .ifPresent(hit -> {
                    log.warn("Screening HIT for userId={} name='{}' list={}", userId, name, hit.getRestrictiveList());
                    throw new ScreeningHitException(hit.getRestrictiveList(), hit.getStatus(), name);
                });

        results.stream()
                .filter(r -> r.getStatus() == ScreeningStatus.FUZZY_HIT)
                .forEach(r -> log.warn("Screening FUZZY_HIT for userId={} name='{}' list={}", userId, name, r.getRestrictiveList()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistResults(List<ScreeningResult> results) {
        screeningResultRepository.saveAll(results);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScreeningResult> getResultsByUserId(Long userId) {
        return screeningResultRepository.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveHit(Long userId) {
        return screeningResultRepository.existsByUserIdAndStatusIn(
                userId, List.of(ScreeningStatus.HIT, ScreeningStatus.FUZZY_HIT));
    }

    @Override
    @Transactional
    public void reviewResult(Long resultId, Long officerId, String notes) {
        ScreeningResult result = screeningResultRepository.findById(resultId)
                .orElseThrow(() -> new EntityNotFoundException("ScreeningResult not found: " + resultId));
        result.setReviewedByOfficerId(officerId);
        result.setOfficerNotes(notes);
        result.setReviewedAt(LocalDateTime.now());
        screeningResultRepository.save(result);
        log.info("Screening result {} reviewed by officer {}", resultId, officerId);
    }
}