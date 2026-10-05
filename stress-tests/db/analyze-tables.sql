-- ============================================================================
-- analyze-tables.sql · refresca las estadísticas de las 25 tablas más grandes
--
-- Correr como verygana_app (el monitor solo tiene SELECT y no puede analizar), ANTES de
-- table-sizes.sql, para que los tamaños y las filas estimadas no sean los de hace un día
-- (information_schema_stats_expiry). No usa procedimientos almacenados: solo sentencias
-- preparadas de la sesión. No imprime datos de usuarios, solo nombres de tabla.
--   MYSQL_USER=verygana_app MYSQL_PASSWORD=lt-app-local stress-tests/scripts/run-sql.sh analyze-tables.sql
-- ============================================================================

SELECT GROUP_CONCAT(CONCAT('`', TABLE_NAME, '`') ORDER BY DATA_LENGTH + INDEX_LENGTH DESC)
  INTO @lt_tables
FROM (
  SELECT TABLE_NAME, DATA_LENGTH, INDEX_LENGTH
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
  ORDER BY DATA_LENGTH + INDEX_LENGTH DESC
  LIMIT 25
) t;

SET @lt_sql = CONCAT('ANALYZE TABLE ', @lt_tables);
PREPARE lt_stmt FROM @lt_sql;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
