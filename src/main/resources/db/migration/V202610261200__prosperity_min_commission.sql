-- Comisión sobre la porción absorbida por el Saldo de Prosperidad (MP-05).
-- Lo absorbido ya no queda en 0 %: paga el 5 % (IVA incluido), que cubre los costos de
-- pasarela y payouts; la base comisionable restante sigue pagando el % del plan.
--
-- Idempotente: cada paso se salta si ya está aplicado.

-- Porcentaje que pagó lo absorbido en el ítem (snapshot). NULL si no hubo absorción o si
-- el ítem es anterior a este cambio.
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'purchase_items'
      AND column_name = 'prosperity_min_commission_pct'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE purchase_items ADD COLUMN prosperity_min_commission_pct DECIMAL(5,2) NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- PlanDataInitializer solo siembra con la tabla plans vacía: los entornos ya sembrados
-- reciben la feature aquí. Solo STANDARD tiene Saldo de Prosperidad.
INSERT INTO features (code, name, type)
SELECT 'PROSPERITY_MIN_COMMISSION_PCT', 'Comisión sobre la porción absorbida por el Saldo de Prosperidad (%)', 'PERCENTAGE'
WHERE NOT EXISTS (SELECT 1 FROM features WHERE code = 'PROSPERITY_MIN_COMMISSION_PCT');

INSERT INTO plan_features (plan_id, feature_id, decimal_value)
SELECT p.id, f.id, 5.00
FROM plans p
JOIN features f ON f.code = 'PROSPERITY_MIN_COMMISSION_PCT'
WHERE p.code = 'STANDARD'
  AND NOT EXISTS (SELECT 1 FROM plan_features pf WHERE pf.plan_id = p.id AND pf.feature_id = f.id);
