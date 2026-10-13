-- MP-12: Libro Mayor de Llaves (Contrato B 2.25, 6.22.7).
--
-- key_transactions pasa a ser un libro mayor de verdad: cada movimiento guarda su
-- secuencia por billetera, el saldo disponible anterior y posterior de cada bolsa,
-- una clave de idempotencia única, el origen tipado y, si compensa a otro, a cuál.
-- La reserva de un copago ahora también escribe fila (antes no, y el libro no cuadraba
-- con el saldo), por eso existe blocked_purchase_delta_cents.
--
-- key_lots lleva el remanente de llaves por fecha de vencimiento: el gasto consume
-- primero lo que vence antes y el vencimiento extingue lotes concretos, en vez de
-- restar min(saldo, suma vencida) sin saber qué se había gastado. Con eso sobra
-- expiry_processed, que era el único campo que se actualizaba en el libro.
--
-- Sin backfill: las filas anteriores quedan con origin_type = 'LEGACY', sin saldos ni
-- secuencia, y los saldos previos de las billeteras no tienen lote (se pueden gastar,
-- no vencen). Ver KeyLedgerServiceImpl.
--
-- Idempotente: cada bloque se salta si ya se aplicó.

-- ── key_wallets: contador de secuencia del libro ────────────────────────────
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'key_wallets' AND column_name = 'last_sequence'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE key_wallets ADD COLUMN last_sequence BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── key_transactions: columnas del libro ────────────────────────────────────
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'key_transactions' AND column_name = 'idempotency_key'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE key_transactions
        ADD COLUMN seq_no BIGINT NULL,
        ADD COLUMN purchase_balance_before_cents BIGINT NULL,
        ADD COLUMN purchase_balance_after_cents BIGINT NULL,
        ADD COLUMN connectivity_balance_before_cents BIGINT NULL,
        ADD COLUMN connectivity_balance_after_cents BIGINT NULL,
        ADD COLUMN blocked_purchase_delta_cents BIGINT NULL,
        ADD COLUMN origin_type ENUM(''AD'',''ADMIN'',''COPAYMENT'',''EXPIRY'',''GAME'',''LEGACY'',''PET_GAME'',''PURCHASE_ITEM_REFUND'',''SURVEY'') NOT NULL DEFAULT ''LEGACY'',
        ADD COLUMN reverses_id BINARY(16) NULL,
        ADD COLUMN idempotency_key VARCHAR(120) NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Las filas previas necesitan una clave para poder exigir NOT NULL + UNIQUE.
UPDATE key_transactions SET idempotency_key = CONCAT('LEGACY:', LOWER(HEX(id))) WHERE idempotency_key IS NULL;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'key_transactions' AND index_name = 'uk_kt_idempotency'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE key_transactions
        MODIFY COLUMN idempotency_key VARCHAR(120) NOT NULL,
        ADD UNIQUE KEY uk_kt_idempotency (idempotency_key),
        ADD UNIQUE KEY uk_kt_wallet_sequence (key_wallet_id, seq_no),
        ADD CONSTRAINT fk_kt_reverses FOREIGN KEY (reverses_id) REFERENCES key_transactions (id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- expiry_processed deja de existir (su índice idx_kt_expiry_processed cae con la columna).
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'key_transactions' AND column_name = 'expiry_processed'
);
SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE key_transactions DROP COLUMN expiry_processed',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── Lotes de llaves ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS key_lots (
  id               BIGINT NOT NULL AUTO_INCREMENT,
  key_wallet_id    BINARY(16) NOT NULL,
  pocket           ENUM('CONNECTIVITY','PURCHASE') NOT NULL,
  expires_at       DATETIME(6) NOT NULL,
  issued_cents     BIGINT NOT NULL,
  remaining_cents  BIGINT NOT NULL,
  version          BIGINT NOT NULL,
  created_at       DATETIME(6) NOT NULL,
  updated_at       DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_key_lot_wallet_pocket_expiry (key_wallet_id, pocket, expires_at),
  KEY idx_key_lot_expiry (expires_at, remaining_cents),
  CONSTRAINT fk_key_lot_wallet FOREIGN KEY (key_wallet_id) REFERENCES key_wallets (id),
  CONSTRAINT chk_key_lot_remaining CHECK (remaining_cents >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Qué lotes tocó cada movimiento del libro: es el "registro de origen" del vencimiento
-- (6.22.7) y lo que permite devolver una reserva liberada a sus mismos lotes.
CREATE TABLE IF NOT EXISTS key_lot_movements (
  id                  BIGINT NOT NULL AUTO_INCREMENT,
  key_transaction_id  BINARY(16) NOT NULL,
  key_lot_id          BIGINT NOT NULL,
  delta_cents         BIGINT NOT NULL,
  created_at          DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_key_lot_movement_tx (key_transaction_id),
  KEY idx_key_lot_movement_lot (key_lot_id),
  CONSTRAINT fk_key_lot_movement_tx FOREIGN KEY (key_transaction_id) REFERENCES key_transactions (id),
  CONSTRAINT fk_key_lot_movement_lot FOREIGN KEY (key_lot_id) REFERENCES key_lots (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── Libro append-only ───────────────────────────────────────────────────────
-- La entidad JPA ya es @Immutable; esto lo garantiza también frente a SQL manual.
--
-- En key_transactions el UPDATE no se rechaza: se neutraliza, devolviendo cada columna
-- del movimiento a su valor anterior. Así sigue funcionando la única corrección legítima
-- que existe, repuntar pet_catalog_item_id al deduplicar el catálogo de mascotas
-- (db/seed/beta/pet-catalog-baked.sql), que no es un dato del movimiento.
DROP TRIGGER IF EXISTS trg_key_transactions_freeze;
CREATE TRIGGER trg_key_transactions_freeze BEFORE UPDATE ON key_transactions
FOR EACH ROW SET
  NEW.key_wallet_id = OLD.key_wallet_id,
  NEW.seq_no = OLD.seq_no,
  NEW.type = OLD.type,
  NEW.origin_type = OLD.origin_type,
  NEW.purchase_keys_delta_cents = OLD.purchase_keys_delta_cents,
  NEW.connectivity_keys_delta_cents = OLD.connectivity_keys_delta_cents,
  NEW.blocked_purchase_delta_cents = OLD.blocked_purchase_delta_cents,
  NEW.purchase_balance_before_cents = OLD.purchase_balance_before_cents,
  NEW.purchase_balance_after_cents = OLD.purchase_balance_after_cents,
  NEW.connectivity_balance_before_cents = OLD.connectivity_balance_before_cents,
  NEW.connectivity_balance_after_cents = OLD.connectivity_balance_after_cents,
  NEW.reason = OLD.reason,
  NEW.reference_id = OLD.reference_id,
  NEW.reverses_id = OLD.reverses_id,
  NEW.idempotency_key = OLD.idempotency_key,
  NEW.expires_at = OLD.expires_at,
  NEW.created_at = OLD.created_at;

DROP TRIGGER IF EXISTS trg_key_transactions_no_delete;
CREATE TRIGGER trg_key_transactions_no_delete BEFORE DELETE ON key_transactions
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'key_transactions es append-only: registre un movimiento compensatorio';

DROP TRIGGER IF EXISTS trg_key_lot_movements_no_update;
CREATE TRIGGER trg_key_lot_movements_no_update BEFORE UPDATE ON key_lot_movements
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'key_lot_movements es append-only';

DROP TRIGGER IF EXISTS trg_key_lot_movements_no_delete;
CREATE TRIGGER trg_key_lot_movements_no_delete BEFORE DELETE ON key_lot_movements
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'key_lot_movements es append-only';
