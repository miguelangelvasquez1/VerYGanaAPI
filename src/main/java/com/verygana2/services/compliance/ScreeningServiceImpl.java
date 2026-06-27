package com.verygana2.services.compliance;

import java.time.LocalDateTime;
import java.util.List;

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
    public void screenOrThrow(Long userId, String nombre, String documentoConsultado) {
        List<ScreeningOutcome> outcomes;
        try {
            outcomes = screeningPort.screen(nombre, documentoConsultado);
        } catch (Exception e) {
            // Si el proveedor falla, registramos pero no bloqueamos el registro (fail open)
            log.error("Screening provider falló para userId={} nombre='{}': {}", userId, nombre, e.getMessage());
            return;
        }

        List<ScreeningResult> results = outcomes.stream()
                .map(o -> ScreeningResult.builder()
                        .userId(userId)
                        .nombreConsultado(nombre)
                        .documentoConsultado(documentoConsultado)
                        .lista(o.lista())
                        .status(o.status())
                        .referenceId(o.referenceId())
                        .rawResponse(o.rawResponse())
                        .build())
                .toList();

        // Persistir en TX independiente para que los registros sobrevivan si la TX externa hace rollback
        persistResults(results);

        results.stream()
                .filter(r -> r.getStatus() == ScreeningStatus.HIT)
                .findFirst()
                .ifPresent(hit -> {
                    log.warn("Screening HIT para userId={} nombre='{}' lista={}", userId, nombre, hit.getLista());
                    throw new ScreeningHitException(hit.getLista(), hit.getStatus(), nombre);
                });

        results.stream()
                .filter(r -> r.getStatus() == ScreeningStatus.FUZZY_HIT)
                .forEach(r -> log.warn("Screening FUZZY_HIT para userId={} nombre='{}' lista={}", userId, nombre, r.getLista()));
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
        log.info("Screening result {} revisado por officer {}", resultId, officerId);
    }
}