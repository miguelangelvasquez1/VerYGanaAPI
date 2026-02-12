package com.verygana2.repositories.raffles;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.raffles.TicketEarningRuleType;
import com.verygana2.models.raffles.TicketEarningRule;

@Repository
public interface TicketEarningRuleRepository extends JpaRepository<TicketEarningRule, Long> {
    
    /**
     * Encuentra reglas activas por tipo, ordenadas por prioridad
     */
    List<TicketEarningRule> findByRuleTypeAndIsActiveTrueOrderByPriorityDesc(TicketEarningRuleType ruleType);

    /**
     * Encuentra reglas por tipo y por estado ordenadas por prioridad
     */
    @Query("""
            SELECT r FROM TicketEarningRule r 
            WHERE (:ruleType IS NULL OR r.ruleType = :ruleType)
            AND (:isActive IS NULL OR r.isActive = :isActive)
            ORDER BY r.priority DESC 
            """)
    List<TicketEarningRule> findByRuleTypeAndIsActiveOrderByPriorityDesc(@Param("ruleType") TicketEarningRuleType ruleType, @Param("isActive") Boolean isActive, Pageable pageable);
    
    /**
     * Todas las reglas activas
     */
    List<TicketEarningRule> findByIsActiveTrueOrderByPriorityDesc();
    
    /**
     * Busca regla por nombre
     */
    Optional<TicketEarningRule> findByRuleName(String ruleName);
    
    /**
     * Verifica si existe una regla con ese nombre
     */
    boolean existsByRuleName(String ruleName);
    
    /**
     * Cuenta reglas activas
     */
    long countByIsActiveTrue();
}