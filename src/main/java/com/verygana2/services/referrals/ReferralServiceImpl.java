package com.verygana2.services.referrals;

import com.verygana2.dtos.referral.responses.ReferralItemDTO;
import com.verygana2.models.User;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.services.interfaces.ReferralService;
import com.verygana2.utils.referral.ReferralCodeGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.verygana2.mappers.ReferralMapper;

import java.util.List;

@Service
public class ReferralServiceImpl implements ReferralService {

    private final ConsumerDetailsRepository consumerDetailsRepository;
    private final ReferralMapper referralMapper;

    public ReferralServiceImpl(ConsumerDetailsRepository consumerDetailsRepository, ReferralMapper referralMapper) {
        this.consumerDetailsRepository = consumerDetailsRepository;
        this.referralMapper = referralMapper;
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
    }

    @Override
    @Transactional(readOnly = true)
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
