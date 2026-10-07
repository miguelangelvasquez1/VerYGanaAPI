package com.verygana2.repositories.finance;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.finance.KeyPocket;
import com.verygana2.models.finance.KeyLot;

@Repository
public interface KeyLotRepository extends JpaRepository<KeyLot, Long> {

    Optional<KeyLot> findByKeyWalletIdAndPocketAndExpiresAt(UUID keyWalletId, KeyPocket pocket, ZonedDateTime expiresAt);

    /** Lotes con saldo de una bolsa, primero el que vence antes (orden de consumo). */
    @Query("""
            SELECT l FROM KeyLot l
            WHERE l.keyWallet.id = :walletId AND l.pocket = :pocket AND l.remainingCents > 0
            ORDER BY l.expiresAt ASC, l.id ASC
            """)
    List<KeyLot> findOpenByWalletAndPocket(@Param("walletId") UUID walletId, @Param("pocket") KeyPocket pocket);

    /** Lotes ya vencidos de una billetera que todavía tienen saldo por extinguir. */
    @Query("""
            SELECT l FROM KeyLot l
            WHERE l.keyWallet.id = :walletId AND l.expiresAt <= :now AND l.remainingCents > 0
            ORDER BY l.expiresAt ASC, l.id ASC
            """)
    List<KeyLot> findDueByWallet(@Param("walletId") UUID walletId, @Param("now") ZonedDateTime now);

    @Query("""
            SELECT DISTINCT l.keyWallet.id FROM KeyLot l
            WHERE l.expiresAt <= :now AND l.remainingCents > 0
            """)
    List<UUID> findWalletIdsWithDueLots(@Param("now") ZonedDateTime now);

    /**
     * Billeteras cuyos lotes suman más que su saldo disponible. Lo contrario es normal:
     * los saldos anteriores a MP-12 no tienen lote.
     */
    @Query("""
            SELECT w.id FROM KeyWallet w
            WHERE w.purchaseKeysCents < (SELECT COALESCE(SUM(l.remainingCents), 0) FROM KeyLot l
                                         WHERE l.keyWallet = w
                                           AND l.pocket = com.verygana2.models.enums.finance.KeyPocket.PURCHASE)
               OR w.connectivityKeysCents < (SELECT COALESCE(SUM(l.remainingCents), 0) FROM KeyLot l
                                             WHERE l.keyWallet = w
                                               AND l.pocket = com.verygana2.models.enums.finance.KeyPocket.CONNECTIVITY)
            """)
    List<UUID> findWalletIdsWithLotsAboveBalance();
}
