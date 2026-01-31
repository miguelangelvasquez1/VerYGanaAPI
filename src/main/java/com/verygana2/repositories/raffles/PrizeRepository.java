package com.verygana2.repositories.raffles;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.raffles.PrizeStatus;
import com.verygana2.models.raffles.Prize;

@Repository
public interface PrizeRepository extends JpaRepository<Prize, Long> {
    
    /**
     * Encuentra todos los premios de una rifa
     */
    List<Prize> findByRaffleIdOrderByPositionAsc(Long raffleId);
    
    /**
     * Encuentra el premio principal (posición 1)
     */
    Optional<Prize> findByRaffleIdAndPosition(Long raffleId, Integer position);
    
    /**
     * Encuentra premios por estado
     */
    List<Prize> findByRaffleIdAndPrizeStatus(Long raffleId, PrizeStatus status);
    
    /**
     * Cuenta premios de una rifa
     */
    long countByRaffleId(Long raffleId);
    
    /**
     * Premios que aún tienen disponibilidad (quantity > claimedCount)
     */
    @Query("SELECT p FROM Prize p WHERE p.raffle.id = :raffleId " +
           "AND p.claimedCount < p.quantity")
    List<Prize> findAvailablePrizesByRaffle(@Param("raffleId") Long raffleId);
    
    /**
     * Verifica si una rifa tiene al menos un premio
     */
    boolean existsByRaffleId(Long raffleId);
}