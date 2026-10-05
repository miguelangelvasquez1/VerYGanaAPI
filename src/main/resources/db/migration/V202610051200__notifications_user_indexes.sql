-- Índices para las dos consultas de la campanita, que corren en cada carga de panel:
--   lista:  WHERE user_id = ? ORDER BY created_at DESC LIMIT ?
--   conteo: WHERE user_id = ? AND is_read = false
-- Hasta ahora solo existía el índice de la FK (user_id), así que la lista ordenaba
-- con filesort y el conteo leía todas las filas del usuario.
--
-- Cada paso es condicional (MySQL no soporta CREATE INDEX IF NOT EXISTS): repetir la
-- migración no falla. No crea tablas: compatible con sql_require_primary_key.

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'notifications' AND index_name = 'idx_notifications_user_created'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE notifications ADD INDEX idx_notifications_user_created (user_id, created_at)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'notifications' AND index_name = 'idx_notifications_user_read'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE notifications ADD INDEX idx_notifications_user_read (user_id, is_read)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
