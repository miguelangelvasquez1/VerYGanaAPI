-- Ganancia real de VERyGANA por compra: suma de purchase_items.commission_net_of_vat_cents
-- (commission_cents − commission_vat_cents). Es lo que va a OPERATIONS.
--
-- Idempotente: se salta si la columna ya existe.

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'purchases'
      AND column_name = 'commission_net_of_vat_cents'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE purchases ADD COLUMN commission_net_of_vat_cents BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Las compras existentes toman el valor que ya se deduce de sus columnas.
UPDATE purchases
SET commission_net_of_vat_cents = commission_cents - commission_vat_cents
WHERE commission_net_of_vat_cents = 0 AND commission_cents > 0;
