package com.verygana2.services.raffles;


import java.time.Instant;

import java.util.List;

import org.hibernate.ObjectNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.raffle.requests.CreateTicketEarningRuleRequestDTO;
import com.verygana2.dtos.raffle.requests.UpdateTicketEarningRuleRequestDTO;
import com.verygana2.dtos.raffle.responses.TicketEarningRuleResponseDTO;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.ProductStock.DuplicateResourceException;
import com.verygana2.mappers.raffles.TicketEarningRuleMapper;
import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.TicketEarningRule;
import com.verygana2.repositories.raffles.TicketEarningRuleRepository;
import com.verygana2.services.interfaces.raffles.TicketEarningRuleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TicketEarningRuleServiceImpl implements TicketEarningRuleService {

    private final TicketEarningRuleRepository ruleRepository;
    private final TicketEarningRuleMapper ruleMapper;

    @Override
    @Transactional(readOnly = true)
    public TicketEarningRule getTicketEarningRuleById(Long ruleId) {
        if (ruleId == null || ruleId <= 0) {
            throw new IllegalArgumentException("Ticket earning rule id must be positive");
        }

        return ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ObjectNotFoundException("Ticket rule with id: " + ruleId + " not found ",
                        TicketEarningRule.class));
    }

    @Override
    @Transactional(readOnly = true)
    public TicketEarningRuleResponseDTO getTicketEarningRuleResponseDTOById(Long ruleId) {
        TicketEarningRule rule = getTicketEarningRuleById(ruleId);
        return ruleMapper.toRuleResponseDTO(rule);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketEarningRuleResponseDTO> getTicketEarningRulesList(TicketEarningRuleType type, Boolean isActive, Pageable pageable) {
        List<TicketEarningRule> rules = ruleRepository
                .findByRuleTypeAndIsActiveOrderByPriorityDesc(type, isActive, pageable);
        
        return rules.stream()
                .map(ruleMapper::toRuleResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketEarningRule> getActiveRulesByType(TicketEarningRuleType type) {

        return ruleRepository.findByRuleTypeAndIsActiveTrueOrderByPriorityDesc(type);
    }

    @Override
    @SuppressWarnings("null")
    public EntityCreatedResponseDTO createTicketEarningRule(Long adminId, CreateTicketEarningRuleRequestDTO request) {
        // Validar que el nombre no existe
        if (ruleRepository.existsByRuleName(request.getRuleName())) {
            throw new DuplicateResourceException(
                    "Rule with name '" + request.getRuleName() + "' already exists");
        }

        // Validar condiciones según el tipo
        validateRuleConditions(request.getRuleType(), request);

        // Crear y guardar regla
        TicketEarningRule rule = buildRuleFromRequest(adminId, request);
        TicketEarningRule savedRule = ruleRepository.save(rule);

        log.info("✅ Created ticket earning rule: {} (ID: {})", savedRule.getRuleName(), savedRule.getId());

        return new EntityCreatedResponseDTO(
                savedRule.getId(),
                "Ticket earning rule created successfully",
                Instant.now());
    }

    @Override
    public EntityUpdatedResponseDTO updateTicketEarningRule(Long adminId, Long ruleId, UpdateTicketEarningRuleRequestDTO request) {
        
        TicketEarningRule rule = getTicketEarningRuleById(ruleId);

        // Validar nombre duplicado (excepto el actual)
        if (!rule.getRuleName().equals(request.getRuleName()) &&
                ruleRepository.existsByRuleName(request.getRuleName())) {
            throw new DuplicateResourceException(
                    "Rule with name '" + request.getRuleName() + "' already exists");
        }

        // Validar condiciones
        validateRuleConditions(request.getRuleType(), request);

        // Actualizar campos
        updateRuleFromRequest(adminId, rule, request);
        TicketEarningRule updatedRule = ruleRepository.save(rule);

        log.info("✅ Updated ticket earning rule: {} (ID: {})", updatedRule.getRuleName(), updatedRule.getId());

        return new EntityUpdatedResponseDTO(
                updatedRule.getId(),
                "Ticket earning rule updated successfully",
                Instant.now());
    }

    @Override
    public void deleteTicketEarningRule(Long ruleId) {

        TicketEarningRule rule = getTicketEarningRuleById(ruleId);
        
        // Verificar que no esté en uso
        if (!rule.getRaffleRules().isEmpty()) {
            throw new InvalidRequestException(
                    "Cannot delete rule that is associated with raffles. Deactivate it instead.");
        }

        ruleRepository.delete(rule);
        log.info("🗑️ Deleted ticket earning rule: {} (ID: {})", rule.getRuleName(), rule.getId());
    }

    @Override
    public void activateTicketEarningRule(Long ruleId) {
        
        TicketEarningRule rule = getTicketEarningRuleById(ruleId);
        rule.setActive(true);
        ruleRepository.save(rule);
        
        log.info("✅ Activated ticket earning rule: {} (ID: {})", rule.getRuleName(), rule.getId());
    }

    @Override
    public void deactivateTicketEarningRule(Long ruleId) {
        TicketEarningRule rule = getTicketEarningRuleById(ruleId);
        rule.setActive(false);
        ruleRepository.save(rule);
        
        log.info("⏸️ Deactivated ticket earning rule: {} (ID: {})", rule.getRuleName(), rule.getId());
    }

    // ==================== VALIDACIONES ====================

    private void validateRuleConditions(TicketEarningRuleType ruleType, Object request) {
        switch (ruleType) {
            case PURCHASE -> {
                if (request instanceof CreateTicketEarningRuleRequestDTO create) {
                    if (create.getMinPurchaseAmount() == null) {
                        throw new InvalidRequestException(
                                "Min purchase amount is required for PURCHASE rules");
                    }
                } else if (request instanceof UpdateTicketEarningRuleRequestDTO update) {
                    if (update.getMinPurchaseAmount() == null) {
                        throw new InvalidRequestException(
                                "Min purchase amount is required for PURCHASE rules");
                    }
                }
            }

            case ADS_WATCHED -> {
                if (request instanceof CreateTicketEarningRuleRequestDTO create) {
                    if (create.getMinAdsWatched() == null) {
                        throw new InvalidRequestException(
                                "Min ads watched is required for ADS_WATCHED rules");
                    }
                } else if (request instanceof UpdateTicketEarningRuleRequestDTO update) {
                    if (update.getMinAdsWatched() == null) {
                        throw new InvalidRequestException(
                                "Min ads watched is required for ADS_WATCHED rules");
                    }
                }
            }

            case GAME_ACHIEVEMENT -> {
                if (request instanceof CreateTicketEarningRuleRequestDTO create) {
                    if (create.getAchievementType() == null || create.getAchievementType().isBlank()) {
                        throw new InvalidRequestException(
                                "Achievement type is required for GAME_ACHIEVEMENT rules");
                    }
                } else if (request instanceof UpdateTicketEarningRuleRequestDTO update) {
                    if (update.getAchievementType() == null || update.getAchievementType().isBlank()) {
                        throw new InvalidRequestException(
                                "Achievement type is required for GAME_ACHIEVEMENT rules");
                    }
                }
            }

            case REFERRAL -> {
                if (request instanceof CreateTicketEarningRuleRequestDTO create) {
                    if (create.getReferralAddedQuantity() == null) {
                        throw new InvalidRequestException(
                                "Referral added quantity is required for REFERRAL rules");
                    }
                } else if (request instanceof UpdateTicketEarningRuleRequestDTO update) {
                    if (update.getReferralAddedQuantity() == null) {
                        throw new InvalidRequestException(
                                "Referral added quantity is required for REFERRAL rules");
                    }
                }
            }

        }
    }
    // ==================== CONSTRUCCIÓN DE ENTIDADES ====================

    private TicketEarningRule buildRuleFromRequest(Long adminId, CreateTicketEarningRuleRequestDTO request) {
        TicketEarningRule rule = new TicketEarningRule();
        rule.setCreatedBy(adminId);
        rule.setRuleName(request.getRuleName());
        rule.setDescription(request.getDescription());
        rule.setRuleType(request.getRuleType());
        rule.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        rule.setTicketsToAward(request.getTicketsToAward());
        rule.setMinPurchaseAmount(request.getMinPurchaseAmount());
        rule.setMinAdsWatched(request.getMinAdsWatched());
        rule.setAchievementType(request.getAchievementType());
        rule.setReferralAddedQuantity(request.getReferralAddedQuantity());
        return rule;
    }

    private void updateRuleFromRequest(Long adminId, TicketEarningRule rule, UpdateTicketEarningRuleRequestDTO request) {
        rule.setRuleName(request.getRuleName());
        rule.setLastModifiedBy(adminId);
        rule.setDescription(request.getDescription());
        rule.setRuleType(request.getRuleType());
        rule.setPriority(request.getPriority());
        rule.setTicketsToAward(request.getTicketsToAward());
        rule.setMinPurchaseAmount(request.getMinPurchaseAmount());
        rule.setMinAdsWatched(request.getMinAdsWatched());
        rule.setAchievementType(request.getAchievementType());
        rule.setReferralAddedQuantity(request.getReferralAddedQuantity());
    }

}