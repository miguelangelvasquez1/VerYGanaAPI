package com.verygana2.repositories.finance.prosperity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.prosperity.ProsperityAccount;

import jakarta.persistence.LockModeType;

@Repository
public interface ProsperityAccountRepository extends JpaRepository<ProsperityAccount, Long> {

    Optional<ProsperityAccount> findByCommercialId(Long commercialId);

    /**
     * Toda escritura en el libro pasa por aquí: serializa por comercial para que dos
     * ventas o una venta y una inversión concurrentes nunca lean el mismo saldo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ProsperityAccount a WHERE a.commercial.id = :commercialId")
    Optional<ProsperityAccount> findByCommercialIdForUpdate(@Param("commercialId") Long commercialId);
}
