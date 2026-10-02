-- ============================================================================
-- table-sizes.sql · tamaño en disco (datos + índices) por tabla
--
-- Solo lectura sobre information_schema.TABLES. information_schema_stats_expiry vale 86400 por
-- defecto y devolvería cifras viejas: se pone en 0 para esta sesión. Para cifras exactas de las
-- tablas grandes, correr antes ANALYZE TABLE como verygana_app (ver GUIDE-local.md); el monitor
-- solo tiene SELECT y no puede analizar. Tamaños en MB; table_rows es una estimación de InnoDB.
-- ============================================================================

SET SESSION information_schema_stats_expiry = 0;

SELECT '== 1. Tamaño por tabla (MB), de mayor a menor' AS seccion;
SELECT TABLE_NAME AS table_name,
       TABLE_ROWS AS est_rows,
       ROUND(DATA_LENGTH / 1048576, 2) AS data_mb,
       ROUND(INDEX_LENGTH / 1048576, 2) AS index_mb,
       ROUND((DATA_LENGTH + INDEX_LENGTH) / 1048576, 2) AS total_mb
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'verygana' AND TABLE_TYPE = 'BASE TABLE'
ORDER BY (DATA_LENGTH + INDEX_LENGTH) DESC, TABLE_NAME;

SELECT '== 2. Total del esquema' AS seccion;
SELECT COUNT(*) AS tables,
       ROUND(SUM(DATA_LENGTH) / 1048576, 2) AS data_mb,
       ROUND(SUM(INDEX_LENGTH) / 1048576, 2) AS index_mb,
       ROUND(SUM(DATA_LENGTH + INDEX_LENGTH) / 1048576, 2) AS total_mb
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'verygana' AND TABLE_TYPE = 'BASE TABLE';
