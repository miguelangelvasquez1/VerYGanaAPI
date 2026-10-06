-- Bloqueo optimista en key_wallets (KeyWallet.version).
--
-- La billetera de llaves se leía, se modificaba en memoria y se guardaba sin ninguna
-- protección: dos operaciones simultáneas del mismo consumidor (un gasto y una
-- recompensa, o dos gastos) partían del mismo saldo y la última en guardar pisaba a
-- la otra. Las rutas de escritura ahora bloquean la fila (FOR UPDATE); esta columna
-- es la red de seguridad para la que se salte el bloqueo.
--
-- Idempotente: una base de dev vieja puede tener ya la columna por ddl-auto: update.
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'key_wallets' AND column_name = 'version'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE key_wallets ADD COLUMN version bigint NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
