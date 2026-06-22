package com.verygana2.services.referrals;

import com.verygana2.dtos.referral.responses.ReferralItemDTO;
import com.verygana2.event.XpAwardRequestedEvent;
import com.verygana2.models.User;
import com.verygana2.models.enums.ActivityType;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.services.interfaces.ReferralService;
import com.verygana2.utils.referral.ReferralCodeGenerator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.verygana2.mappers.ReferralMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Service
public class ReferralServiceImpl implements ReferralService {

    private final ConsumerDetailsRepository consumerDetailsRepository;
    private final ReferralMapper referralMapper;
    private final ApplicationEventPublisher eventPublisher;

    public ReferralServiceImpl(ConsumerDetailsRepository consumerDetailsRepository,
                               ReferralMapper referralMapper,
                               ApplicationEventPublisher eventPublisher) {
        this.consumerDetailsRepository = consumerDetailsRepository;
        this.referralMapper = referralMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String generateUniqueCode(int length) {
        for (int i = 0; i < 10; i++) {
            String code = ReferralCodeGenerator.codeGenerator(length);
            // normaliza para evitar duplicados por case
            code = code.toUpperCase();

            if (!consumerDetailsRepository.existsByReferralCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("No se pudo generar referral_code único");
    }

    /**
     * Completa la parte de referidos sobre un ConsumerDetails NUEVO (no persistido aún):
     *  - referralCode propio
     *  - referredBy (si viene código)
     */
    @Transactional(readOnly = true)
    @Override
    public void prepareNewConsumer(User user, ConsumerDetails details, String referredByCode) {
        details.setUser(user);

        // 1) SIEMPRE: código propio del usuario
        details.setReferralCode(generateUniqueCode(8));

        // 2) OPCIONAL: asignar quién lo refirió
        applyReferredBy(details, referredByCode);
    }

    private void applyReferredBy(ConsumerDetails consumer, String referredByCode) {
        if (referredByCode == null || referredByCode.isBlank()) return;

        String normalized = referredByCode.trim().toUpperCase();

        ConsumerDetails referrer = consumerDetailsRepository.findByReferralCode(normalized)
                .orElseThrow(() -> new RuntimeException("Código de referido inválido"));

        // Anti auto-referido (mejor por email porque el ID del user puede no existir aún)
        if (referrer.getUser() != null && consumer.getUser() != null
                && referrer.getUser().getEmail() != null
                && referrer.getUser().getEmail().equalsIgnoreCase(consumer.getUser().getEmail())) {
            throw new RuntimeException("No se permite auto-referido");
        }

        // No sobrescribir si ya tenía referidor
        if (consumer.getReferredBy() != null) return;

        consumer.setReferredBy(referrer);

        eventPublisher.publishEvent(
                new XpAwardRequestedEvent(this, referrer.getId(), ActivityType.REFERRAL_ACTIVE));
    }

    @Override
    @Transactional
    public List<ReferralItemDTO> getReferralsByEmail(String email) {

        ConsumerDetails referrer = consumerDetailsRepository
                .findByUserEmail(email)
                .orElseThrow(() -> new RuntimeException(
                        "ConsumerDetails no encontrado para: " + email));

        return consumerDetailsRepository
                .findReferralsByReferrer(referrer)
                .stream()
                .map(referralMapper::toDTO)
                .toList();
    }
}
