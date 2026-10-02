-- ============================================================================
-- snapshot.sql · contadores de la BD para las métricas de la prueba
--
-- Solo lectura. Se corre ANTES y DESPUÉS de cada corrida; los deltas dividen por la duración:
--   consultas/s = Δ Questions / s        IOPS lectura = Δ Innodb_data_reads / s
--   IOPS escritura = Δ Innodb_data_writes / s     (scripts/snapshot-delta.sh hace la cuenta)
-- Los contadores son acumulados desde el arranque del servidor: sin delta no significan nada.
-- Los valores instantáneos (Threads_connected, Threads_running) y Max_used_connections se leen
-- del "después". Max_used_connections es acumulado: se recrea el contenedor entre planes.
-- Solo variables de estado del servidor: sin SQL_TEXT, correos ni datos de usuarios.
-- Como verygana_monitor (PROCESS + SELECT sobre performance_schema).
-- ============================================================================

SELECT 'snapshot_utc' AS metric, DATE_FORMAT(UTC_TIMESTAMP(3), '%Y-%m-%dT%H:%i:%s.%fZ') AS value;

-- Contadores y valores de estado (clave, valor), ordenados para poder comparar con diff.
SELECT CONCAT('status.', VARIABLE_NAME) AS metric, VARIABLE_VALUE AS value
FROM performance_schema.global_status
WHERE VARIABLE_NAME IN (
  'Uptime', 'Questions', 'Queries', 'Slow_queries',
  'Connections', 'Aborted_connects', 'Aborted_clients', 'Connection_errors_max_connections',
  'Threads_connected', 'Threads_running', 'Threads_created', 'Max_used_connections',
  'Innodb_data_reads', 'Innodb_data_writes', 'Innodb_data_read', 'Innodb_data_written',
  'Innodb_data_fsyncs', 'Innodb_os_log_written',
  'Innodb_buffer_pool_reads', 'Innodb_buffer_pool_read_requests',
  'Innodb_buffer_pool_write_requests', 'Innodb_buffer_pool_wait_free',
  'Innodb_buffer_pool_pages_total', 'Innodb_buffer_pool_pages_data', 'Innodb_buffer_pool_pages_dirty',
  'Innodb_row_lock_waits', 'Innodb_row_lock_time', 'Innodb_rows_read', 'Innodb_rows_inserted',
  'Innodb_rows_updated', 'Innodb_rows_deleted',
  'Created_tmp_tables', 'Created_tmp_disk_tables', 'Select_full_join', 'Select_scan', 'Sort_merge_passes',
  'Bytes_received', 'Bytes_sent', 'Handler_commit', 'Handler_rollback')
ORDER BY VARIABLE_NAME;

-- Sentencias por tipo. performance_schema.global_status no trae los contadores Com_*; este resumen sí
-- (acumulado desde el arranque, igual que los demás).
SELECT CONCAT('stmt.', SUBSTRING_INDEX(EVENT_NAME, '/', -1)) AS metric, COUNT_STAR AS value
FROM performance_schema.events_statements_summary_global_by_event_name
WHERE EVENT_NAME IN ('statement/sql/select', 'statement/sql/insert', 'statement/sql/update',
                     'statement/sql/delete', 'statement/sql/commit')
ORDER BY EVENT_NAME;

-- Techos configurados (contexto del plan evaluado).
SELECT CONCAT('variable.', VARIABLE_NAME) AS metric, VARIABLE_VALUE AS value
FROM performance_schema.global_variables
WHERE VARIABLE_NAME IN ('max_connections', 'innodb_buffer_pool_size', 'innodb_flush_log_at_trx_commit',
                        'sql_require_primary_key', 'log_bin_trust_function_creators', 'version')
ORDER BY VARIABLE_NAME;

-- Tamaño de las colas de brandeo y de solicitudes de mascotas: cola = estados pendientes.
SELECT CONCAT('queue.branding_requests.', status) AS metric, COUNT(*) AS value
FROM verygana.branding_requests GROUP BY status ORDER BY status;
SELECT CONCAT('queue.catalog_integration_requests.', status) AS metric, COUNT(*) AS value
FROM verygana.catalog_integration_requests GROUP BY status ORDER BY status;
