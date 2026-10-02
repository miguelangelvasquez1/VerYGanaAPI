-- ============================================================================
-- top-digests.sql · consultas más costosas y lentas 
--
-- Solo lectura sobre performance_schema.events_statements_summary_by_digest. Usa DIGEST_TEXT,
-- que MySQL entrega normalizado (los literales ya son "?"): NUNCA SQL_TEXT, que traería correos,
-- teléfonos o documentos. El slow log de MySQL queda apagado por la misma razón.
-- Los contadores son acumulados desde el último TRUNCATE de la tabla o el arranque: para aislar
-- una corrida, vaciar el resumen como root justo antes (ver GUIDE-local.md) o restar con
-- los valores de un volcado previo. Tiempos en ms (los timers vienen en picosegundos).
-- ============================================================================

SELECT '== 1. Top 25 por tiempo total' AS seccion;
SELECT COUNT_STAR AS calls,
       ROUND(SUM_TIMER_WAIT / 1e9, 1) AS total_ms,
       ROUND(AVG_TIMER_WAIT / 1e9, 2) AS avg_ms,
       ROUND(MAX_TIMER_WAIT / 1e9, 1) AS max_ms,
       SUM_ROWS_EXAMINED AS rows_examined,
       SUM_ROWS_SENT AS rows_sent,
       SUM_NO_INDEX_USED AS no_index_used,
       SUM_CREATED_TMP_DISK_TABLES AS tmp_disk,
       LEFT(DIGEST_TEXT, 300) AS digest_text
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = 'verygana'
ORDER BY SUM_TIMER_WAIT DESC
LIMIT 25;

SELECT '== 2. Top 25 por promedio (al menos 20 llamadas)' AS seccion;
SELECT COUNT_STAR AS calls,
       ROUND(AVG_TIMER_WAIT / 1e9, 2) AS avg_ms,
       ROUND(MAX_TIMER_WAIT / 1e9, 1) AS max_ms,
       ROUND(SUM_TIMER_WAIT / 1e9, 1) AS total_ms,
       SUM_ROWS_EXAMINED AS rows_examined,
       SUM_NO_INDEX_USED AS no_index_used,
       LEFT(DIGEST_TEXT, 300) AS digest_text
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = 'verygana' AND COUNT_STAR >= 20
ORDER BY AVG_TIMER_WAIT DESC
LIMIT 25;

SELECT '== 3. Lentas: promedio > 100 ms o máximo > 1 s (al menos 5 llamadas)' AS seccion;
SELECT COUNT_STAR AS calls,
       ROUND(AVG_TIMER_WAIT / 1e9, 2) AS avg_ms,
       ROUND(MAX_TIMER_WAIT / 1e9, 1) AS max_ms,
       SUM_NO_INDEX_USED AS no_index_used,
       LEFT(DIGEST_TEXT, 300) AS digest_text
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = 'verygana' AND COUNT_STAR >= 5
  AND (AVG_TIMER_WAIT > 100e9 OR MAX_TIMER_WAIT > 1000e9)
ORDER BY MAX_TIMER_WAIT DESC
LIMIT 25;
