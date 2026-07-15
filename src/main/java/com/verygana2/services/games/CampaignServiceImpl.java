package com.verygana2.services.games;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.game.campaign.CampaignDTO;
import com.verygana2.dtos.game.campaign.CampaignSummaryDTO;
import com.verygana2.dtos.game.campaign.UpdateCampaignRequestDTO;
import com.verygana2.dtos.game.campaign.UpdateCampaignRequestDTO.TargetAudienceDTO;
import com.verygana2.mappers.CampaignMapper;
import com.verygana2.models.Category;
import com.verygana2.models.TargetAudience;
import com.verygana2.models.branding.Campaign;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.finance.plans.RequirePlanCapability;
import com.verygana2.models.finance.plans.RequirePlanCapability.Capability;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.services.interfaces.CampaignService;
import com.verygana2.services.interfaces.CategoryService;
import com.verygana2.utils.validators.TargetingValidator;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CampaignServiceImpl implements CampaignService {
    
    @PersistenceContext
    private EntityManager entityManager;

    private final CategoryService categoryService;
    private final TargetingValidator targetingValidator;
    private final CampaignMapper campaignMapper;
    private final CampaignRepository campaignRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<CampaignSummaryDTO> getCommercialCampaigns(Long commercialId) {
        List<Campaign> campaigns = campaignRepository.findByCommercialId(commercialId);
        return campaigns.stream().map(campaignMapper::toSummaryDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignDTO getCampaignDetail(Long campaignId, Long userId) {
        Campaign campaign = campaignRepository.findByIdAndCommercialId(Objects.requireNonNull(campaignId), userId)
                .orElseThrow(() -> new EntityNotFoundException("Campaña no encontrada"));
        return campaignMapper.toDto(campaign);
    }

    @Override
    @RequirePlanCapability(value = {Capability.CAN_USE_GAMES}, commercialIdParam = "userId")
    public void updateCampaignStatus(Long campaignId, Long userId, CampaignStatus newStatus) {

        Campaign campaign = campaignRepository.findByIdAndCommercialId(Objects.requireNonNull(campaignId), userId)
                .orElseThrow(() -> new EntityNotFoundException("Campaña no encontrada"));

        CampaignStatus currentStatus = campaign.getStatus();

        // 2. Validar transición
        validateStatusTransition(campaign, currentStatus, newStatus);

        // 3. Aplicar transición
        campaign.setStatus(newStatus);


        // 4. Reglas laterales según estado
        handleSideEffects(campaign, newStatus);

        campaignRepository.save(campaign);
    }

    @Override
    @RequirePlanCapability(value = {Capability.CAN_USE_GAMES}, commercialIdParam = "userId")
    public void updateCampaign(Long campaignId, Long userId, UpdateCampaignRequestDTO request) {

        Campaign campaign = campaignRepository.findByIdAndCommercialId(Objects.requireNonNull(campaignId), userId)
                .orElseThrow(() -> new EntityNotFoundException("Campaña no encontrada"));

        // 1. Restricciones por estado
        if (campaign.getStatus() == CampaignStatus.CANCELLED ||
            campaign.getStatus() == CampaignStatus.COMPLETED) {
            throw new ValidationException("No se puede editar una campaña cancelada o completada");
        }

        // 2. Máximo de sesiones por usuario por día
        if (request.getMaxSessionsPerUserPerDay() != null) {
            campaign.setMaxSessionsPerUserPerDay(request.getMaxSessionsPerUserPerDay());
        }

        // 3. Audiencia
        applyTargetAudience(campaign, request);

        campaignRepository.save(campaign);
    }

    private void validateStatusTransition(Campaign campaign, CampaignStatus from, CampaignStatus to) {

        if (from == to) {
            throw new ValidationException("La campaña ya está en ese estado");
        }

        switch (to) {
            case DRAFT -> throw new ValidationException("No se puede transicionar a DRAFT");
            case ACTIVE -> {
                if (from != CampaignStatus.DRAFT && from != CampaignStatus.PAUSED) {
                    throw new ValidationException("Solo se puede activar una campaña en DRAFT o PAUSED");
                }
                if (!hasCompleteTargeting(campaign)) {
                    throw new ValidationException(
                        "La segmentación de audiencia (categorías, edad, género) y maxSessionsPerUserPerDay "
                            + "deben estar completos antes de activar la campaña");
                }
            }
            case PAUSED -> {
                if (from != CampaignStatus.ACTIVE) {
                    throw new ValidationException("Solo se puede pausar una campaña ACTIVE");
                }
            }
            case CANCELLED -> {
                if (from != CampaignStatus.ACTIVE && from != CampaignStatus.PAUSED) {
                    throw new ValidationException("Solo se puede cancelar una campaña ACTIVE o PAUSED");
                }
            }
            case COMPLETED -> throw new ValidationException("El estado COMPLETED no se puede asignar manualmente");
        }
    }

    private boolean hasCompleteTargeting(Campaign campaign) {
        TargetAudience ta = campaign.getTargetAudience();
        if (ta == null) return false;

        List<Category> categories = ta.getCategories();
        return categories != null && !categories.isEmpty()
            && ta.getMinAge() != null
            && ta.getMaxAge() != null
            && ta.getTargetGender() != null
            && campaign.getMaxSessionsPerUserPerDay() != null;
    }

    private void 
    handleSideEffects(Campaign campaign, CampaignStatus newStatus) {

        ZonedDateTime now = ZonedDateTime.now(clock);

        switch (newStatus) {
            case ACTIVE -> {
                if (campaign.getStartDate() == null) {
                    campaign.setStartDate(now);
                }
            }
            case CANCELLED -> {
                if (campaign.getEndDate() == null) {
                    campaign.setEndDate(now);
                }
            }
            default -> {
            }
        }
    }

    private void applyTargetAudience(Campaign campaign, UpdateCampaignRequestDTO request) {
        TargetAudienceDTO dto = request.getTargetAudience();

        TargetAudience ta = campaign.getTargetAudience();
        if (ta == null) {
            ta = new TargetAudience();
            campaign.setTargetAudience(ta);
        }

        if (request.getCategoryIds() != null) {
            ta.setCategories(categoryService.getValidatedCategories(request.getCategoryIds()));
        }

        if (dto != null) {
            if (dto.getMinAge() != null && dto.getMaxAge() != null && dto.getMinAge() > dto.getMaxAge()) {
                throw new ValidationException("Rango de edad inválido");
            }
            if (dto.getMinAge() != null) ta.setMinAge(dto.getMinAge());
            if (dto.getMaxAge() != null) ta.setMaxAge(dto.getMaxAge());
            if (dto.getGender() != null) ta.setTargetGender(dto.getGender());
            if (dto.getMunicipalityCodes() != null) {
                ta.setTargetMunicipalities(targetingValidator.getValidatedMunicipalities(dto.getMunicipalityCodes()));
            }
        }
    }
}