package com.verygana2.services.compliance;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.config.zapsign.ZapSignConfig;
import com.verygana2.dtos.zapsign.ZapSignCheckResponseDTO;
import com.verygana2.dtos.zapsign.ZapSignCreateCheckRequestDTO;
import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.compliance.BackgroundCheck;
import com.verygana2.models.enums.BackgroundCheckStatus;
import com.verygana2.models.enums.BackgroundCheckType;
import com.verygana2.models.enums.commercial.PersonType;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialContractRepository;
import com.verygana2.repositories.compliance.BackgroundCheckRepository;
import com.verygana2.services.interfaces.compliance.BackgroundCheckService;
import com.verygana2.services.zapsign.ZapSignClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BackgroundCheckServiceImpl implements BackgroundCheckService {

    private final CommercialContractRepository contractRepository;
    private final BackgroundCheckRepository backgroundCheckRepository;
    private final ZapSignClient zapSignClient;
    private final ZapSignConfig zapSignConfig;

    @Override
    public List<BackgroundCheck> requestChecks(Long contractId, Long officerId) {
        CommercialContract contract = getContractOrThrow(contractId);
        CommercialOnboarding onboarding = contract.getOnboarding();
        CommercialDetails details = onboarding.getCommercialDetails();

        List<BackgroundCheck> created = new ArrayList<>();
        created.add(requestPersonCheck(contractId, officerId, onboarding, details));

        if (onboarding.getPersonType() == PersonType.JURIDICA) {
            created.add(requestCompanyCheck(contractId, officerId, details));
        }

        return created;
    }

    private BackgroundCheck requestPersonCheck(Long contractId, Long officerId,
            CommercialOnboarding onboarding, CommercialDetails details) {
        String fullName = (nullSafe(onboarding.getLegalRepFirstName()) + " " + nullSafe(onboarding.getLegalRepLastName())).trim();

        ZapSignCreateCheckRequestDTO request = ZapSignCreateCheckRequestDTO.builder()
                .country(zapSignConfig.getBackgroundCheckCountry())
                .type("person")
                .userAuthorized(true)
                .nationalId(details.getLegalRepDocNumber())
                .firstName(onboarding.getLegalRepFirstName())
                .lastName(onboarding.getLegalRepLastName())
                .forceCreation(true)
                .customInput("contract-" + contractId + "-legalrep")
                .build();

        ZapSignCheckResponseDTO response = zapSignClient.createCheck(request);

        return saveFromResponse(contractId, officerId, BackgroundCheckType.PERSON,
                fullName, details.getLegalRepDocNumber(), response);
    }

    private BackgroundCheck requestCompanyCheck(Long contractId, Long officerId, CommercialDetails details) {
        ZapSignCreateCheckRequestDTO request = ZapSignCreateCheckRequestDTO.builder()
                .country(zapSignConfig.getBackgroundCheckCountry())
                .type("company")
                .userAuthorized(true)
                .taxId(details.getNit())
                .companyName(details.getCompanyName())
                .forceCreation(true)
                .customInput("contract-" + contractId + "-company")
                .build();

        ZapSignCheckResponseDTO response = zapSignClient.createCheck(request);

        return saveFromResponse(contractId, officerId, BackgroundCheckType.COMPANY,
                details.getCompanyName(), details.getNit(), response);
    }

    private BackgroundCheck saveFromResponse(Long contractId, Long officerId, BackgroundCheckType type,
            String subjectName, String subjectDocument, ZapSignCheckResponseDTO response) {
        BackgroundCheck check = BackgroundCheck.builder()
                .contractId(contractId)
                .checkId(response.getCheckId())
                .checkType(type)
                .country(response.getCountry())
                .subjectName(subjectName)
                .subjectDocument(subjectDocument)
                .status(mapStatus(response.getStatus()))
                .score(response.getScore())
                .pdfReportUrl(response.getPdfReport() != null ? response.getPdfReport().getUrl() : null)
                .requestedByOfficerId(officerId)
                .build();

        return backgroundCheckRepository.save(check);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BackgroundCheck> listByContract(Long contractId) {
        return backgroundCheckRepository.findByContractIdOrderByRequestedAtDesc(contractId);
    }

    @Override
    public BackgroundCheck refreshStatus(Long backgroundCheckId) {
        BackgroundCheck check = getCheckOrThrow(backgroundCheckId);
        ZapSignCheckResponseDTO response = zapSignClient.getCheckStatus(check.getCheckId());
        return applyStatus(check, response);
    }

    @Override
    public void handleWebhookCompleted(String checkId) {
        backgroundCheckRepository.findByCheckId(checkId).ifPresentOrElse(check -> {
            ZapSignCheckResponseDTO response = zapSignClient.getCheckStatus(checkId);
            applyStatus(check, response);
        }, () -> log.warn("[ZAPSIGN CHECK WEBHOOK] Ningún background check local con checkId={}", checkId));
    }

    private BackgroundCheck applyStatus(BackgroundCheck check, ZapSignCheckResponseDTO response) {
        BackgroundCheckStatus status = mapStatus(response.getStatus());
        check.setStatus(status);
        check.setScore(response.getScore());
        if (response.getPdfReport() != null) {
            check.setPdfReportUrl(response.getPdfReport().getUrl());
        }
        if (status == BackgroundCheckStatus.COMPLETED && check.getCompletedAt() == null) {
            check.setCompletedAt(ZonedDateTime.now());
        }
        return backgroundCheckRepository.save(check);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getDetail(Long backgroundCheckId) {
        BackgroundCheck check = getCheckOrThrow(backgroundCheckId);
        return zapSignClient.getCheckDetail(check.getCheckId());
    }

    private BackgroundCheckStatus mapStatus(String status) {
        if (status == null) {
            return BackgroundCheckStatus.NOT_STARTED;
        }
        try {
            return BackgroundCheckStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[ZAPSIGN CHECK] Estado desconocido '{}', se guarda como IN_PROGRESS", status);
            return BackgroundCheckStatus.IN_PROGRESS;
        }
    }

    private CommercialContract getContractOrThrow(Long contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new ObjectNotFoundException("Contrato no encontrado: " + contractId, CommercialContract.class));
    }

    private BackgroundCheck getCheckOrThrow(Long backgroundCheckId) {
        return backgroundCheckRepository.findById(backgroundCheckId)
                .orElseThrow(() -> new ObjectNotFoundException("Background check no encontrado: " + backgroundCheckId, BackgroundCheck.class));
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }
}
