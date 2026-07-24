package com.verygana2.services.commercial;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZonedDateTime;
import java.util.Map;

import org.hibernate.ObjectNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.exceptions.commercial.OnboardingStepException;
import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialContractRepository;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.services.interfaces.commercial.ESignaturePort;
import com.verygana2.services.interfaces.commercial.ESignatureService;
import com.verygana2.storage.service.R2Service;
import com.verygana2.utils.audit.AuditEvent;
import com.verygana2.utils.audit.AuditLevel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ESignatureServiceImpl implements ESignatureService {

    private final CommercialContractRepository contractRepository;
    private final CommercialOnboardingRepository onboardingRepository;
    private final R2Service r2Service;
    private final ESignaturePort esignaturePort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void requestSignature(Long contractId) {
        CommercialContract contract = getContractOrThrow(contractId);
        CommercialOnboarding onboarding = contract.getOnboarding();
        CommercialDetails details = onboarding.getCommercialDetails();

        byte[] pdfBytes;
        try (var stream = r2Service.getPrivateObjectStream(contract.getObjectKey())) {
            pdfBytes = stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "No se pudo leer el PDF del contrato para enviarlo a firma: " + contractId, e);
        }

        String signerEmail = details.getUser().getEmail();
        String signerName = (nullSafe(onboarding.getLegalRepFirstName()) + " " + nullSafe(onboarding.getLegalRepLastName())).trim();

        EsignatureEnvelope envelope = esignaturePort.sendForSignature(new SignatureRequest(
                contractId, signerName, signerEmail, pdfBytes,
                "contrato-marco-v" + contract.getVersion() + ".pdf"));

        contract.setStatus(ContractStatus.PENDING_SIGNATURE);
        contract.setEsignatureProvider(envelope.provider());
        contract.setEsignatureEnvelopeId(envelope.envelopeId());
        contract.setEsignatureSentAt(ZonedDateTime.now());
        contract.setEsignatureSignerEmail(signerEmail);
        contractRepository.save(contract);

        onboarding.setCurrentStep(OnboardingStep.SIGNATURE_PENDING);
        onboardingRepository.save(onboarding);

        publishAudit(details.getId(), "COMMERCIAL_CONTRACT_SENT_FOR_SIGNATURE",
                "Se envió el Contrato Marco v" + contract.getVersion() + " a firma electrónica de "
                        + signerEmail + " (proveedor: " + envelope.provider() + ").",
                Map.of("contractId", contractId, "envelopeId", envelope.envelopeId(), "provider", envelope.provider()));
    }

    @Override
    public void markSigned(Long contractId, ZonedDateTime signedAt) {
        CommercialContract contract = getContractOrThrow(contractId);
        if (contract.getStatus() != ContractStatus.PENDING_SIGNATURE) {
            throw new OnboardingStepException("El contrato no está pendiente de firma.");
        }

        contract.setStatus(ContractStatus.SIGNED);
        contract.setEsignatureSignedAt(signedAt);
        contractRepository.save(contract);

        CommercialOnboarding onboarding = contract.getOnboarding();
        onboarding.setCurrentStep(OnboardingStep.PAYMENT_PENDING);
        onboardingRepository.save(onboarding);

        publishAudit(onboarding.getCommercialDetails().getId(), "COMMERCIAL_CONTRACT_SIGNED",
                "El Contrato Marco v" + contract.getVersion() + " fue firmado. Onboarding pasa a pendiente de pago.",
                Map.of("contractId", contractId));
    }

    private CommercialContract getContractOrThrow(Long contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new ObjectNotFoundException("Contrato no encontrado: " + contractId, CommercialContract.class));
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    private void publishAudit(Long userId, String action, String description, Map<String, Object> additionalData) {
        try {
            eventPublisher.publishEvent(AuditEvent.builder()
                    .userId(userId)
                    .action(action)
                    .level(AuditLevel.INFO)
                    .category("COMPLIANCE")
                    .description(description)
                    .className(ESignatureServiceImpl.class.getName())
                    .timestamp(ZonedDateTime.now())
                    .success(true)
                    .additionalData(additionalData)
                    .build());
        } catch (Exception e) {
            log.error("No se pudo publicar el evento de auditoría para la acción: {}", action, e);
        }
    }
}
