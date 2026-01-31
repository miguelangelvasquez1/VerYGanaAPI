package com.verygana2.repositories.raffles;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.enums.raffles.RuleType;
import com.verygana2.models.raffles.TicketEarningRule;

@Repository
public interface TicketEarningRuleRepository extends JpaRepository<TicketEarningRule, Long> {
    
    /**
     * Encuentra reglas activas por tipo, ordenadas por prioridad
     */
    List<TicketEarningRule> findByRuleTypeAndIsActiveTrueOrderByPriorityDesc(RuleType ruleType);

    /**
     * Encuentra reglas por tipo y por estado ordenadas por prioridad
     */
    List<TicketEarningRule> findByRuleTypeAndIsActiveOrderByPriorityDesc(RuleType ruleType, boolean isActive, Pageable pageable);
    
    /**
     * Todas las reglas activas
     */
    List<TicketEarningRule> findByIsActiveTrueOrderByPriorityDesc();
    
    /**
     * Reglas por tipo de rifa
     */
    List<TicketEarningRule> findByAppliesToRaffleTypeAndIsActiveTrue(RaffleType raffleType);
    
    /**
     * Busca regla por nombre
     */
    Optional<TicketEarningRule> findByRuleName(String ruleName);
    
    /**
     * Verifica si existe una regla con ese nombre
     */
    boolean existsByRuleName(String ruleName);
    
    /**
     * Reglas activas que aún tienen cupo disponible
     */
    @Query("SELECT r FROM TicketEarningRule r WHERE r.isActive = true " +
           "AND (r.maxUsesGlobal IS NULL OR r.currentUsesCount < r.maxUsesGlobal) " +
           "AND r.ruleType = :ruleType " +
           "ORDER BY r.priority DESC")
    List<TicketEarningRule> findAvailableRulesByType(@Param("ruleType") RuleType ruleType);
    
    /**
     * Cuenta reglas activas
     */
    long countByIsActiveTrue();
}