-- MP-12: al cerrar una promoción especial de llaves, lo que no está comprometido vuelve a
-- FORTIFICATION de inmediato. Antes se esperaba a que no quedara ninguna compra con pago
-- en curso para devolver todo junto, y una sola compra pendiente retenía el fondo entero.
-- Como ahora puede devolverse por partes, hay que llevar cuánto se ha devuelto.
--
-- Idempotente: el bloque se salta si la columna ya existe.
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'key_coverage_promotions'
      AND column_name = 'guarantee_returned_cents'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE key_coverage_promotions ADD COLUMN guarantee_returned_cents BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Las promociones que ya habían devuelto su fondo lo hicieron completo, menos lo aportado.
UPDATE key_coverage_promotions
SET guarantee_returned_cents = budget_cents - guarantee_drawn_cents
WHERE guarantee_returned_at IS NOT NULL AND guarantee_returned_cents = 0;
