-- ============================================================================
-- Sembrado de la prueba de carga · 04 · preferencias del consumidor
--
-- ConsumerDetails.categories lleva @Size(min = 1): un consumidor sin ninguna categoría no
-- pasa la validación al editarlo. Cada consumidor recibe @lt_m_consumer_preferences (3)
-- categorías distintas, escalonadas de a 3 sobre el catálogo para que se repartan.
-- consumer_preferences no tiene primary key : la idempotencia es el NOT EXISTS.
-- ============================================================================

INSERT INTO consumer_preferences (user_id, category_id)
WITH RECURSIVE seq (j) AS (SELECT 0 UNION ALL SELECT j + 1 FROM seq WHERE j < @lt_m_consumer_preferences - 1),
cat AS (SELECT id, ROW_NUMBER() OVER (ORDER BY id) - 1 AS rn FROM categories),
cons AS (
    SELECT u.id AS uid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM consumer_preferences cp WHERE cp.user_id = u.id)
)
SELECT c.uid, ct.id
FROM cons c
JOIN seq s
JOIN cat ct ON ct.rn = MOD(c.n + 3 * s.j, (SELECT COUNT(*) FROM cat));
