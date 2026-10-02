-- Verificación de compatibilidad con DO. Correr como verygana_monitor:
--   docker compose ... exec -T mysql mysql -uverygana_monitor -plt-monitor-local verygana < stress-tests/db/check-schema.sql
SHOW VARIABLES WHERE Variable_name IN (
  'sql_require_primary_key','log_bin','binlog_format','gtid_mode','enforce_gtid_consistency',
  'log_bin_trust_function_creators','performance_schema','innodb_buffer_pool_size',
  'max_connections','character_set_server','collation_server','time_zone','version');

-- Tablas base del esquema sin primary key (V1 falla con require_primary_key=ON si hay alguna).
SELECT t.TABLE_NAME AS table_without_pk
FROM information_schema.TABLES t
LEFT JOIN information_schema.TABLE_CONSTRAINTS c
  ON c.TABLE_SCHEMA = t.TABLE_SCHEMA AND c.TABLE_NAME = t.TABLE_NAME AND c.CONSTRAINT_TYPE = 'PRIMARY KEY'
WHERE t.TABLE_SCHEMA = DATABASE() AND t.TABLE_TYPE = 'BASE TABLE'
  AND t.TABLE_NAME <> 'flyway_schema_history' AND c.CONSTRAINT_NAME IS NULL;

-- Triggers del esquema (se esperan 4, de V9).
SELECT TRIGGER_NAME, EVENT_OBJECT_TABLE FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE();
SELECT COUNT(*) AS trigger_count FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE();

-- Privilegios del usuario con el que se corre el script. Para verificar al usuario de la app
-- (no debe tener SUPER, SYSTEM_VARIABLES_ADMIN ni SET_USER_ID), correr este script con
-- -uverygana_app; el monitor no puede leer los grants de otro usuario.
SHOW GRANTS;
