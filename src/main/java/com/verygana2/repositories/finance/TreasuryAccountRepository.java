package com.verygana2.repositories.finance;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.models.finance.TreasuryAccount;

import jakarta.persistence.LockModeType;

@Repository
public interface TreasuryAccountRepository extends JpaRepository<TreasuryAccount, UUID> {

    Optional<TreasuryAccount> findByCode(TreasuryAccountCode code);

    boolean existsByCode(TreasuryAccountCode code);

    /**
     * Busca una cuenta con PESSIMISTIC_WRITE lock.
     * Usado por TreasuryService antes de modificar saldos para evitar
     * race conditions cuando dos depósitos llegan simultáneamente.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TreasuryAccount t WHERE t.code = :code")
    Optional<TreasuryAccount> findByCodeForUpdate(@Param("code") TreasuryAccountCode code);

    /** Solo el id, sin cargar la entidad: para armar referencias sin leer el saldo. */
    @Query("SELECT t.id FROM TreasuryAccount t WHERE t.code = :code")
    Optional<UUID> findIdByCode(@Param("code") TreasuryAccountCode code);

    /**
     * Debita la cuenta solo si el saldo alcanza, en una sola sentencia.
     *
     * Reemplaza al par SELECT … FOR UPDATE + UPDATE en los flujos de alta frecuencia:
     * el bloqueo de fila se toma aquí y no antes, así que dura lo que falte de la
     * transacción en vez de toda ella. La condición del WHERE la evalúa la base con
     * la fila ya bloqueada, por eso el saldo nunca queda negativo aunque lleguen N
     * débitos a la vez.
     *
     * Es un UPDATE masivo: no pasa por {@code @PreUpdate} (de ahí el {@code now}) ni
     * refresca una {@link TreasuryAccount} que ya esté cargada en la sesión. No
     * mezclar con {@link #findByCodeForUpdate} sobre la misma cuenta en una misma
     * transacción.
     *
     * @return 1 si debitó, 0 si el saldo no cubre el monto (o la cuenta no existe)
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE TreasuryAccount t
               SET t.balanceCents = t.balanceCents - :amountCents, t.updatedAt = :now
             WHERE t.code = :code AND t.balanceCents >= :amountCents
            """)
    int debitIfCovered(@Param("code") TreasuryAccountCode code, @Param("amountCents") long amountCents,
            @Param("now") ZonedDateTime now);

    /** Acredita la cuenta en una sola sentencia. Mismas salvedades que {@link #debitIfCovered}. */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE TreasuryAccount t
               SET t.balanceCents = t.balanceCents + :amountCents, t.updatedAt = :now
             WHERE t.code = :code
            """)
    int credit(@Param("code") TreasuryAccountCode code, @Param("amountCents") long amountCents,
            @Param("now") ZonedDateTime now);

    @Query("SELECT COUNT(t) FROM TreasuryAccount t WHERE t.balanceCents < 0")
    long countNegativeBalances();
}