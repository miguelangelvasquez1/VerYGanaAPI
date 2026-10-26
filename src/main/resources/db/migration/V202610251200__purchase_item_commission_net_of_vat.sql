-- Ganancia real de VERyGANA por ítem: la comisión sin el IVA que ya trae
-- (commission_cents − commission_vat_cents). Es lo que va a OPERATIONS.
--
-- Idempotente: se salta si la columna ya existe.

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'purchase_items'
      AND column_name = 'commission_net_of_vat_cents'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE purchase_items ADD COLUMN commission_net_of_vat_cents BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Las filas existentes toman el valor que ya se deduce de sus columnas; no se recalcula
-- el IVA con la fórmula nueva.
UPDATE purchase_items
SET commission_net_of_vat_cents = commission_cents - commission_vat_cents
WHERE commission_net_of_vat_cents = 0 AND commission_cents > 0;
