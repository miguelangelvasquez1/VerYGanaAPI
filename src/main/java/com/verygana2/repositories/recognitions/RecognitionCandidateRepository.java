package com.verygana2.repositories.recognitions;

import java.time.ZonedDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.recognitions.Recognition;

/**
 * Rankings de usuarios para que el admin elija a quién reconocer. Solo consumidores con la
 * cuenta ACTIVE. Cada consulta devuelve el id interno y la métrica: el servicio resuelve el
 * publicId, el nombre y el nivel, y el id interno nunca sale al frontend.
 */
public interface RecognitionCandidateRepository extends Repository<Recognition, Long> {

    interface CandidateRow {
        Long getConsumerId();

        Long getMetric();
    }

    /** Llaves ganadas por interacción y referidos en el periodo, en centavos de llave. */
    @Query(value = """
            SELECT kw.consumer_id AS consumerId,
                   SUM(COALESCE(kt.purchase_keys_delta_cents, 0) + COALESCE(kt.connectivity_keys_delta_cents, 0)) AS metric
            FROM key_transactions kt
            JOIN key_wallets kw ON kw.id = kt.key_wallet_id
            JOIN users u ON u.id = kw.consumer_id
            WHERE kt.type IN ('CREDIT_INTERACTION', 'CREDIT_REFERRAL_BONUS')
              AND kt.created_at >= :from AND kt.created_at < :to
              AND u.account_status = 'ACTIVE'
            GROUP BY kw.consumer_id
            ORDER BY metric DESC, kw.consumer_id
            """, countQuery = """
            SELECT COUNT(DISTINCT kw.consumer_id)
            FROM key_transactions kt
            JOIN key_wallets kw ON kw.id = kt.key_wallet_id
            JOIN users u ON u.id = kw.consumer_id
            WHERE kt.type IN ('CREDIT_INTERACTION', 'CREDIT_REFERRAL_BONUS')
              AND kt.created_at >= :from AND kt.created_at < :to
              AND u.account_status = 'ACTIVE'
            """, nativeQuery = true)
    Page<CandidateRow> rankByKeysEarned(@Param("from") ZonedDateTime from, @Param("to") ZonedDateTime to,
            Pageable pageable);

    /** Compras completadas por el propio usuario (no reconocimientos recibidos). */
    @Query(value = """
            SELECT p.consumer_id AS consumerId, COUNT(*) AS metric
            FROM purchases p
            JOIN users u ON u.id = p.consumer_id
            WHERE p.buyer_type = 'CONSUMER' AND p.status = 'COMPLETED'
              AND p.completed_at >= :from AND p.completed_at < :to
              AND u.account_status = 'ACTIVE'
            GROUP BY p.consumer_id
            ORDER BY metric DESC, p.consumer_id
            """, countQuery = """
            SELECT COUNT(DISTINCT p.consumer_id)
            FROM purchases p
            JOIN users u ON u.id = p.consumer_id
            WHERE p.buyer_type = 'CONSUMER' AND p.status = 'COMPLETED'
              AND p.completed_at >= :from AND p.completed_at < :to
              AND u.account_status = 'ACTIVE'
            """, nativeQuery = true)
    Page<CandidateRow> rankByPurchasesCount(@Param("from") ZonedDateTime from, @Param("to") ZonedDateTime to,
            Pageable pageable);

    /** Valor de las compras completadas por el usuario, en centavos (dinero + llaves). */
    @Query(value = """
            SELECT p.consumer_id AS consumerId, SUM(p.total_cents) AS metric
            FROM purchases p
            JOIN users u ON u.id = p.consumer_id
            WHERE p.buyer_type = 'CONSUMER' AND p.status = 'COMPLETED'
              AND p.completed_at >= :from AND p.completed_at < :to
              AND u.account_status = 'ACTIVE'
            GROUP BY p.consumer_id
            ORDER BY metric DESC, p.consumer_id
            """, countQuery = """
            SELECT COUNT(DISTINCT p.consumer_id)
            FROM purchases p
            JOIN users u ON u.id = p.consumer_id
            WHERE p.buyer_type = 'CONSUMER' AND p.status = 'COMPLETED'
              AND p.completed_at >= :from AND p.completed_at < :to
              AND u.account_status = 'ACTIVE'
            """, nativeQuery = true)
    Page<CandidateRow> rankByPurchasesAmount(@Param("from") ZonedDateTime from, @Param("to") ZonedDateTime to,
            Pageable pageable);

    @Query(value = """
            SELECT gs.consumer_id AS consumerId, COUNT(*) AS metric
            FROM game_sessions gs
            JOIN users u ON u.id = gs.consumer_id
            WHERE gs.start_time >= :from AND gs.start_time < :to
              AND u.account_status = 'ACTIVE'
            GROUP BY gs.consumer_id
            ORDER BY metric DESC, gs.consumer_id
            """, countQuery = """
            SELECT COUNT(DISTINCT gs.consumer_id)
            FROM game_sessions gs
            JOIN users u ON u.id = gs.consumer_id
            WHERE gs.start_time >= :from AND gs.start_time < :to
              AND u.account_status = 'ACTIVE'
            """, nativeQuery = true)
    Page<CandidateRow> rankByGamesPlayed(@Param("from") ZonedDateTime from, @Param("to") ZonedDateTime to,
            Pageable pageable);

    /** XP acumulado: no depende del periodo. */
    @Query(value = """
            SELECT ulp.consumer_id AS consumerId, ulp.xp_total AS metric
            FROM user_level_profile ulp
            JOIN users u ON u.id = ulp.consumer_id
            WHERE u.account_status = 'ACTIVE'
            ORDER BY ulp.xp_total DESC, ulp.consumer_id
            """, countQuery = """
            SELECT COUNT(*)
            FROM user_level_profile ulp
            JOIN users u ON u.id = ulp.consumer_id
            WHERE u.account_status = 'ACTIVE'
            """, nativeQuery = true)
    Page<CandidateRow> rankByXp(Pageable pageable);
}
