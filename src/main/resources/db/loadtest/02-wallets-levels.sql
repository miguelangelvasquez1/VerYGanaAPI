-- ============================================================================
-- Sembrado de la prueba de carga · 02 · billeteras y niveles
--
-- Montos siempre en centavos (columnas *_cents). Cada fila cuelga de su dueño y se inserta
-- solo si ese dueño aún no la tiene (idempotente).
-- ============================================================================

-- key_wallets: el id es el public_id del consumidor, como en db/seed/test. Cada consumidor
-- parte con entre 10.000 y 50.000 COP (en centavos) de llaves de compra y algo de conectividad.
INSERT INTO key_wallets (id, consumer_id, purchase_keys_cents, blocked_purchase_keys_cents,
                         connectivity_keys_cents, blocked_connectivity_keys_cents, created_at, updated_at)
SELECT u.public_id, u.id,
       1000000 + MOD(u.id * 7919, 4000000), 0,
       100000 + MOD(u.id * 104729, 900000), 0,
       u.registered_date, NOW()
FROM users u
WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
  AND NOT EXISTS (SELECT 1 FROM key_wallets kw WHERE kw.consumer_id = u.id);

-- user_level_profile: el nivel sigue el XP (una escala simple; solo importa que haya de todos).
INSERT INTO user_level_profile (consumer_id, benefits_paused, created_at, current_level, last_activity_at,
                                reactivation_mission_active, xp_total)
SELECT u.id, 0, u.registered_date,
       CASE WHEN MOD(u.id * 97, 5000) < 500 THEN 'BRONCE'
            WHEN MOD(u.id * 97, 5000) < 1500 THEN 'PLATA'
            WHEN MOD(u.id * 97, 5000) < 3000 THEN 'ORO'
            WHEN MOD(u.id * 97, 5000) < 4000 THEN 'ESMERALDA'
            WHEN MOD(u.id * 97, 5000) < 4700 THEN 'RUBI'
            ELSE 'DIAMANTE' END,
       TIMESTAMPADD(HOUR, -MOD(u.id * 13, 72), NOW()),
       0, MOD(u.id * 97, 5000)
FROM users u
WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
  AND NOT EXISTS (SELECT 1 FROM user_level_profile lp WHERE lp.consumer_id = u.id);

-- wallets del comercial: 500.000 COP de presupuesto (50.000.000 centavos), activa.
INSERT INTO wallets (commercial_id, version, balance_cents, status, last_deposit_amount_cents,
                     last_budget_alert_stage, last_updated, created_at)
SELECT u.id, 0, 50000000, 'ACTIVE', 50000000, 'NONE', NOW(), u.registered_date
FROM users u
WHERE u.role = 'COMMERCIAL' AND u.email LIKE 'lt-commercial-%@loadtest.invalid'
  AND NOT EXISTS (SELECT 1 FROM wallets w WHERE w.commercial_id = u.id);
