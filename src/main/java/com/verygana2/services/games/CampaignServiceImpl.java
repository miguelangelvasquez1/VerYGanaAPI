package com.verygana2.services.games;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.BudgetIncreaseResponseDTO;
import com.verygana2.dtos.game.campaign.CampaignDTO;
import com.verygana2.dtos.game.campaign.CampaignSummaryDTO;
import com.verygana2.dtos.game.campaign.IncreaseCampaignBudgetRequestDTO;
import com.verygana2.dtos.game.campaign.UpdateCampaignRequestDTO;
import com.verygana2.dtos.game.campaign.UpdateCampaignRequestDTO.TargetAudienceDTO;
import com.verygana2.exceptions.StaleBudgetException;
import com.verygana2.mappers.CampaignMapper;
import com.verygana2.models.Category;
import com.verygana2.models.TargetAudience;
import com.verygana2.models.branding.Campaign;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.RequirePlanCapability;
import com.verygana2.models.finance.plans.RequirePlanCapability.Capability;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.games.CampaignRepository;
import com.verygana2.services.interfaces.CampaignService;
import com.verygana2.services.interfaces.CategoryService;
import com.verygana2.services.plans.PlanFeatureGuard;
import com.verygana2.utils.concurrency.RetryOnConcurrencyConflict;
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
    private final WalletRepository walletRepository;
    private final PlanFeatureGuard planFeatureGuard;
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
    @RequirePlanCapability(value = {Capability.CAN_USE_GAMES}, commercialIdParam = "userId", blockWhenDormant = true)
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

    /**
     * Estados en los que una campaña admite aumento de presupuesto: los que ya están en su ciclo
     * de vida activo. Quedan fuera DRAFT (aún no se lanzó; el monto se fijó al crear la solicitud
     * de branding) y CANCELLED (terminal).
     */
    private static final Set<CampaignStatus> BUDGET_INCREASE_STATUSES =
            EnumSet.of(CampaignStatus.ACTIVE, CampaignStatus.PAUSED, CampaignStatus.COMPLETED);

    /**
     * Suma {@code additionalBudgetCents} al presupuesto de una campaña y lo descuenta de la wallet.
     *
     * <p>Una campaña COMPLETED (agotó su presupuesto) se reabre: vuelve a ACTIVE. Como los COMPLETED
     * no ocupan cupo del plan, reabrirla exige que quede cupo {@code MAX_BRANDED_GAMES}. ACTIVE y
     * PAUSED conservan su estado.
     *
     * <p>Exige que {@code expectedBudgetCents} coincida con el {@code budgetCents} actual —anti doble
     * cobro: 409 {@link StaleBudgetException} si otro aumento ya se aplicó. El gasto por sesión
     * mueve {@code spentCents} (ver {@link Campaign#chargeSession}), nunca el presupuesto, así que
     * esa comparación no se ve afectada por sesiones en curso.
     *
     * <p>Toma lock pesimista sobre la campaña y sobre la wallet. Todas las validaciones y el cobro
     * ocurren antes de mutar la campaña, así que un fallo no deja nada a medias.
     */
    @Override
    @RequirePlanCapability(value = {Capability.CAN_USE_GAMES}, commercialIdParam = "userId")
    @RetryOnConcurrencyConflict
    public BudgetIncreaseResponseDTO increaseCampaignBudget(Long campaignId, Long userId, IncreaseCampaignBudgetRequestDTO request) {
        Campaign campaign = campaignRepository.findByIdAndCommercialIdForUpdate(Objects.requireNonNull(campaignId), userId)
                .orElseThrow(() -> new EntityNotFoundException("Campaña no encontrada"));

        // Anti doble cobro: budgetCents solo lo cambia un aumento (el gasto por sesión mueve
        // spentCents, no el presupuesto), así que si ya no coincide con lo que el cliente vio, otro
        // aumento (doble clic, reintento, otra pestaña) se aplicó primero. Se compara ya con la fila
        // bloqueada, por lo que dos envíos simultáneos no pasan ambos.
        if (!campaign.getBudgetCents().equals(request.getExpectedBudgetCents())) {
            throw new StaleBudgetException(String.format(
                    "El presupuesto de la campaña cambió: ahora es de %d ¢ y esperabas %d ¢. Puede que el "
                            + "aumento ya se haya aplicado; actualiza la información y vuelve a intentarlo.",
                    campaign.getBudgetCents(), request.getExpectedBudgetCents()));
        }

        if (!BUDGET_INCREASE_STATUSES.contains(campaign.getStatus())) {
            throw new ValidationException(
                    "Solo se puede aumentar el presupuesto de campañas activas, pausadas o completadas. Estado actual: "
                            + campaign.getStatus());
        }

        boolean reopening = campaign.getStatus() == CampaignStatus.COMPLETED;
        if (reopening) {
            planFeatureGuard.assertCanReopen(userId, Capability.MAX_BRANDED_GAMES);
        }

        long additionalBudgetCents = request.getAdditionalBudgetCents();

        Wallet wallet = walletRepository.findByCommercialIdForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet del anunciante no encontrado"));
        wallet.consume(additionalBudgetCents);
        walletRepository.save(wallet);

        campaign.setBudgetCents(campaign.getBudgetCents() + additionalBudgetCents);
        if (reopening) {
            campaign.setStatus(CampaignStatus.ACTIVE);
        }
        campaignRepository.save(campaign);

        log.info("Campaign {} budget increased by {} ¢ for commercial {}{}",
                campaignId, additionalBudgetCents, userId, reopening ? " — reopened from COMPLETED" : "");

        return BudgetIncreaseResponseDTO.builder()
                .assetId(campaign.getId())
                .chargedCents(additionalBudgetCents)
                .totalBudgetCents(campaign.getBudgetCents())
                .remainingBudgetCents(campaign.getBudgetCents() - campaign.getSpentCents())
                .status(campaign.getStatus().name())
                .reopened(reopening)
                .walletBalanceCents(wallet.getBalanceCents())
                .build();
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
            case CANCELLED -> throw new ValidationException("Las campañas no se pueden cancelar");
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