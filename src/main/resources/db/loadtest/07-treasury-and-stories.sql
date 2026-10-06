-- ============================================================================
-- Sembrado de la prueba de carga · 07 · tesorería e historias de impacto
--
-- Tesorería: el flujo de gasto de llaves (POST /consumer/wallet/keys/spend) mueve KEYS_RESERVE a
-- OPERATIONS y el de payouts sale de PAYOUTS_PENDING; con saldo 0 el primero responde 409. Los
-- saldos salen de los conteos del plan (proporcionales entre A y B) y van en centavos. Si
-- TreasuryDataInitializer aún no corrió, las cuentas se crean aquí con sus mismos nombres y él las
-- respeta (busca por code). GREATEST hace idempotente el fondeo: repetir no acumula y crecer de A a B
-- solo sube el saldo al nuevo objetivo. No se escriben movimientos: es un saldo inicial ficticio.
-- ============================================================================

INSERT INTO treasury_accounts (id, balance_cents, code, created_at, name, updated_at)
SELECT UNHEX(REPLACE(UUID(), '-', '')), 0, d.code, NOW(6), d.name, NOW(6)
FROM (SELECT 'KEYS_RESERVE' AS code, 'Reserva de llaves' AS name
      UNION ALL SELECT 'OPERATIONS', 'Operación VeryGana'
      UNION ALL SELECT 'PAYOUTS_PENDING', 'Pagos pendientes a empresarios') d
WHERE NOT EXISTS (SELECT 1 FROM treasury_accounts t WHERE t.code = d.code);

UPDATE treasury_accounts
SET balance_cents = GREATEST(balance_cents, @lt_consumers * @lt_m_treasury_keys_reserve_cents_per_consumer),
    updated_at = NOW(6)
WHERE code = 'KEYS_RESERVE';

UPDATE treasury_accounts
SET balance_cents = GREATEST(balance_cents, @lt_commercials * @lt_m_treasury_payouts_pending_cents_per_commercial),
    updated_at = NOW(6)
WHERE code = 'PAYOUTS_PENDING';

UPDATE treasury_accounts
SET balance_cents = GREATEST(balance_cents, @lt_commercials * @lt_m_treasury_operations_cents_per_commercial),
    updated_at = NOW(6)
WHERE code = 'OPERATIONS';

-- ---------------------------------------------------------------------------
-- Historias de impacto publicadas (globales). No se escribe invested_amount (decimal en pesos, nulo).
-- ---------------------------------------------------------------------------
INSERT INTO impact_stories (author_name, beneficiaries_count, category, created_at, description, invested_currency,
                            location, status, story_date, tags, title, updated_at)
WITH RECURSIVE seq (i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM seq WHERE i < @lt_m_impact_stories)
SELECT 'Equipo de la prueba de carga', 20 + q.i * 3, ELT(1 + MOD(q.i, 3), 'EDUCACION', 'SALUD', 'COMUNIDAD'),
       NOW(6), CONCAT('Historia de impacto ficticia número ', q.i, ' de la prueba de carga.'), 'COP',
       'Medellín', 'PUBLISHED', DATE_SUB(CURDATE(), INTERVAL q.i DAY), 'loadtest,ficticia',
       CONCAT('LT Story ', q.i), NOW(6)
FROM seq q
WHERE NOT EXISTS (SELECT 1 FROM impact_stories x WHERE x.title = CONCAT('LT Story ', q.i));
