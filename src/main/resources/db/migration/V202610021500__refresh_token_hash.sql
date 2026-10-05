-- Refresh tokens: la base guarda solo la huella SHA-256 del token, no el token.
--
-- Hasta ahora refresh_tokens.token (varchar(1024)) guardaba el JWT en claro y no tenía
-- índice, así que cada /auth/refresh y /auth/logout recorría la tabla entera. Ahora la
-- columna es token_hash (hex minúscula de 64 caracteres) con índice único, y la API
-- busca por la huella del token que presenta el cliente (spec 005).
--
-- Cada paso es condicional (MySQL no soporta ADD COLUMN IF NOT EXISTS ni hace DDL
-- transaccional): si la migración se corta a la mitad, repetirla retoma sin fallar.
-- No crea tablas, triggers ni funciones: compatible con sql_require_primary_key.

-- ─── 1. Agregar token_hash (nullable: la tabla puede tener filas) ───────────────
-- Nullable porque un ADD COLUMN ... NOT NULL llenaría las filas existentes con ''
-- y el índice único del paso 4 fallaría por duplicados.
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'refresh_tokens' AND column_name = 'token_hash'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE refresh_tokens ADD COLUMN token_hash varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER jti',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─── 2. Cerrar las sesiones previas ─────────────────────────────────────────────
-- Las filas sin huella son tokens en claro de antes del cambio: no se pueden hashear
-- sin el token y no deben quedar en la base. Los usuarios inician sesión de nuevo.
-- En una segunda corrida no borra nada.
DELETE FROM refresh_tokens WHERE token_hash IS NULL;

-- ─── 3. token_hash NOT NULL (redefinir la columna es idempotente) ───────────────
ALTER TABLE refresh_tokens MODIFY COLUMN token_hash varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

-- ─── 4. Índice único sobre token_hash ───────────────────────────────────────────
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'refresh_tokens' AND index_name = 'uk_rt_token_hash'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE refresh_tokens ADD UNIQUE INDEX uk_rt_token_hash (token_hash)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─── 5. Eliminar la columna con el token en claro ───────────────────────────────
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'refresh_tokens' AND column_name = 'token'
);
SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE refresh_tokens DROP COLUMN token',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
