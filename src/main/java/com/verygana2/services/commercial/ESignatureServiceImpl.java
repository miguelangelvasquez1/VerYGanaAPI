package com.verygana2.services.commercial;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZonedDateTime;
import java.util.Map;

import org.hibernate.ObjectNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.user.commercial.onboarding.EsignatureEnvelope;
import com.verygana2.dtos.user.commercial.onboarding.SignatureRequest;
import com.verygana2.event.ContractSignedEvent;
import com.verygana2.exceptions.commercial.OnboardingStepException;
import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.commercial.ContractPurpose;
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
        CommercialDetails details = contract.getCommercial();
        CommercialOnboarding onboarding = details.getOnboarding();

        byte[] pdfBytes;
        try (var stream = r2Service.getPrivateObjectStream(contract.getObjectKey())) {
            pdfBytes = stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el PDF del contrato para enviarlo a firma: " + contractId, e);
        }

        String signerEmail = details.getUser().getEmail();
        String signerPhoneNumber = details.getUser().getPhoneNumber();
        String signerName = onboarding != null
                ? (nullSafe(onboarding.getLegalRepFirstName()) + " " + nullSafe(onboarding.getLegalRepLastName())).trim()
                : details.getCompanyName();

        EsignatureEnvelope envelope = esignaturePort.sendForSignature(new SignatureRequest(
                contractId, details.getId(), signerName, signerEmail, signerPhoneNumber, pdfBytes,
                contract.getPurpose().name().toLowerCase() + "-v" + contract.getVersion()
                        + details.getLegalRepDocNumber() + ".pdf"));

        contract.setStatus(ContractStatus.PENDING_SIGNATURE);
        contract.setEsignatureProvider(envelope.provider());
        contract.setEsignatureEnvelopeId(envelope.envelopeId());
        contract.setEsignatureSentAt(ZonedDateTime.now());
        contract.setEsignatureSignerEmail(signerEmail);
        contractRepository.save(contract);

        if (contract.getPurpose() == ContractPurpose.ONBOARDING && onboarding != null) {
            onboarding.setCurrentStep(OnboardingStep.SIGNATURE_PENDING);
            onboardingRepository.save(onboarding);
        }

        publishAudit(details.getId(), "COMMERCIAL_CONTRACT_SENT_FOR_SIGNATURE",
                "Se envió el contrato (" + contract.getPurpose() + ") v" + contract.getVersion()
                        + " a firma electrónica de " + signerEmail + " (proveedor: " + envelope.provider() + ").",
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

        CommercialDetails details = contract.getCommercial();

        if (contract.getPurpose() == ContractPurpose.ONBOARDING) {
            CommercialOnboarding onboarding = contract.getOnboarding();
            onboarding.setCurrentStep(OnboardingStep.PAYMENT_PENDING);
            onboardingRepository.save(onboarding);
        }

        publishAudit(details.getId(), "COMMERCIAL_CONTRACT_SIGNED",
                "El contrato (" + contract.getPurpose() + ") v" + contract.getVersion() + " fue firmado.",
                Map.of("contractId", contractId));

        eventPublisher.publishEvent(new ContractSignedEvent(this, contractId, contract.getPurpose()));
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
