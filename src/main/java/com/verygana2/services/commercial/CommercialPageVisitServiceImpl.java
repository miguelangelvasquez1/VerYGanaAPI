package com.verygana2.services.commercial;

import java.time.ZonedDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.commercial.report.RegisterPageVisitRequestDTO;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.commercial.CommercialPageVisit;
import com.verygana2.models.commercial.CommercialPageVisit.PageVisitSource;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.commercial.CommercialPageVisitRepository;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.services.interfaces.commercial.CommercialPageVisitService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommercialPageVisitServiceImpl implements CommercialPageVisitService {

    private final CommercialPageVisitRepository pageVisitRepository;
    private final AdRepository adRepository;
    private final ConsumerDetailsRepository consumerDetailsRepository;

    /** Ventana de deduplicación por (anuncio, consumer). Evita inflar la métrica con doble clic / reintentos. */
    @Value("${commercial.page-visit.dedup-window-minutes:30}")
    private long dedupWindowMinutes;

    @Override
    @Transactional
    public void registerVisit(Long consumerId, RegisterPageVisitRequestDTO request) {
        Ad ad = adRepository.findById(request.getAdId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Anuncio no encontrado: " + request.getAdId()));

        ZonedDateTime dedupThreshold = ZonedDateTime.now().minusMinutes(dedupWindowMinutes);
        if (pageVisitRepository.existsByAdIdAndConsumerIdAndCreatedAtAfter(ad.getId(), consumerId, dedupThreshold)) {
            log.debug("Visita a página ignorada por dedup: ad={}, consumer={}", ad.getId(), consumerId);
            return;
        }

        ConsumerDetails consumer = consumerDetailsRepository.findById(consumerId)
                .orElseThrow(() -> new EntityNotFoundException("Consumer no encontrado: " + consumerId));

        String targetUrl = request.getTargetUrl() != null && !request.getTargetUrl().isBlank()
                ? request.getTargetUrl()
                : ad.getTargetUrl();

        pageVisitRepository.save(CommercialPageVisit.builder()
                .commercial(ad.getCommercial())
                .ad(ad)
                .consumer(consumer)
                .targetUrl(truncate(targetUrl, 500))
                .source(PageVisitSource.AD)
                .userHash(consumer.getUserHash())
                .build());
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
