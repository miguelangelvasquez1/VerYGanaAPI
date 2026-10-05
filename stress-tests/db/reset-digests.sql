-- ============================================================================
-- reset-digests.sql · vacía el resumen de consultas de performance_schema
--
-- Correr como root justo ANTES de una corrida para que top-digests.sql solo vea esa corrida
-- (si no, mezcla el sembrado y las corridas anteriores). Solo toca performance_schema; no hay datos
-- de usuarios en ese resumen (DIGEST_TEXT va normalizado).
--   MYSQL_USER=root MYSQL_PASSWORD=lt-root-local stress-tests/scripts/run-sql.sh reset-digests.sql
-- ============================================================================

TRUNCATE TABLE performance_schema.events_statements_summary_by_digest;
