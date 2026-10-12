package com.verygana2.repositories.finance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.finance.KeyWallet;

import jakarta.persistence.LockModeType;

@Repository
public interface KeyWalletRepository extends JpaRepository<KeyWallet, UUID> {

    // Solo para lectura. Toda ruta que MODIFIQUE el saldo (acreditar, gastar, reservar,
    // liberar + save) debe usar findByConsumerIdForUpdate.
    Optional<KeyWallet> findByConsumerId(Long consumerId);

    /**
     * SELECT ... FOR UPDATE sobre la billetera. Serializa por consumidor los
     * leer-modificar-guardar concurrentes: sin esto, dos operaciones simultáneas del
     * mismo usuario (un gasto en el juego y la recompensa de un like, o dos gastos)
     * leían el mismo saldo y la última en guardar pisaba a la otra.
     *
     * El bloqueo es por usuario, así que no encola a nadie más. Tomarlo ANTES de
     * cualquier bloqueo de tesorería: ese es el orden de todos los flujos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM KeyWallet w WHERE w.consumer.id = :consumerId")
    Optional<KeyWallet> findByConsumerIdForUpdate(@Param("consumerId") Long consumerId);

    /** Mismo bloqueo que {@link #findByConsumerIdForUpdate}, para quien parte del id de la billetera. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM KeyWallet w WHERE w.id = :id")
    Optional<KeyWallet> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByConsumerId (Long consumerId);

    /**
     * Billeteras cuyo saldo no coincide con el saldo posterior de su último asiento.
     * Solo mira las que ya tienen asientos con secuencia (posteriores a MP-12).
     */
    @Query("""
            SELECT w.id FROM KeyWallet w, KeyTransaction t
            WHERE t.keyWallet = w
              AND t.sequence = w.ledgerSequence
              AND (w.purchaseKeysCents <> t.purchaseBalanceAfterCents
                   OR w.connectivityKeysCents <> t.connectivityBalanceAfterCents)
            """)
    List<UUID> findIdsOutOfSyncWithLedger();

    /**
     * Pasivo total de llaves vivas, en centavos de COP.
     *
     * Es la contraparte de TreasuryAccount(KEYS_RESERVE): cada centavo de llave
     * que un consumidor tiene en su billetera es un centavo que la app debe
     * respaldar cuando lo redima en un copago.
     *
     * Suma los cuatro campos, no getAvailableKeysCents(): las llaves bloqueadas
     * por un copago en curso siguen siendo pasivo — o se confirman (y drenan
     * KEYS_RESERVE) o se liberan (y vuelven a estar disponibles).
     *
     * Misma escala que la tesorería: 1 centavo de billetera = 1 centavo de
     * KEYS_RESERVE. keyValueCents solo divide para mostrar "llaves" en la UI.
     */
    @Query("""
            SELECT COALESCE(SUM(w.purchaseKeysCents + w.blockedPurchaseKeysCents
                              + w.connectivityKeysCents + w.blockedConnectivityKeysCents), 0)
            FROM KeyWallet w
            """)
    long sumLiveKeyLiabilityCents();
}
