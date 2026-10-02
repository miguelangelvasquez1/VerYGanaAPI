#!/usr/bin/env bash
# Resta dos salidas de snapshot.sql (antes y después) y calcula las métricas de la BD:
# consultas por segundo, IOPS de lectura y escritura, tasa de fallos del buffer pool, conexiones.
# La duración sale de Δ status.Uptime (segundos), no de relojes externos.
#
# Uso: snapshot-delta.sh <antes.tsv> <despues.tsv>
set -euo pipefail

BEFORE="${1:?uso: snapshot-delta.sh <antes.tsv> <despues.tsv>}"
AFTER="${2:?uso: snapshot-delta.sh <antes.tsv> <despues.tsv>}"

awk -F'\t' '
  FNR == 1 { file++ }
  $1 == "metric" { next }
  { if (file == 1) b[$1] = $2; else a[$1] = $2 }
  END {
    d = a["status.Uptime"] - b["status.Uptime"]
    if (d <= 0) { print "error: Uptime no avanzó (¿el servidor se reinició entre snapshots?)" > "/dev/stderr"; exit 1 }
    dq = a["status.Questions"] - b["status.Questions"]
    dr = a["status.Innodb_data_reads"] - b["status.Innodb_data_reads"]
    dw = a["status.Innodb_data_writes"] - b["status.Innodb_data_writes"]
    drb = a["status.Innodb_data_read"] - b["status.Innodb_data_read"]
    dwb = a["status.Innodb_data_written"] - b["status.Innodb_data_written"]
    dbr = a["status.Innodb_buffer_pool_reads"] - b["status.Innodb_buffer_pool_reads"]
    dbq = a["status.Innodb_buffer_pool_read_requests"] - b["status.Innodb_buffer_pool_read_requests"]
    printf "metric\tvalue\n"
    printf "duration_s\t%d\n", d
    printf "queries_per_s\t%.1f\n", dq / d
    printf "select_per_s\t%.1f\n", (a["stmt.select"] - b["stmt.select"]) / d
    printf "write_stmts_per_s\t%.1f\n", ((a["stmt.insert"] - b["stmt.insert"]) + (a["stmt.update"] - b["stmt.update"]) + (a["stmt.delete"] - b["stmt.delete"])) / d
    printf "iops_read\t%.1f\n", dr / d
    printf "iops_write\t%.1f\n", dw / d
    printf "read_mb_per_s\t%.2f\n", drb / d / 1048576
    printf "write_mb_per_s\t%.2f\n", dwb / d / 1048576
    printf "buffer_pool_miss_ratio\t%.5f\n", (dbq > 0 ? dbr / dbq : 0)
    printf "slow_queries_delta\t%d\n", a["status.Slow_queries"] - b["status.Slow_queries"]
    printf "tmp_disk_tables_delta\t%d\n", a["status.Created_tmp_disk_tables"] - b["status.Created_tmp_disk_tables"]
    printf "row_lock_waits_delta\t%d\n", a["status.Innodb_row_lock_waits"] - b["status.Innodb_row_lock_waits"]
    printf "aborted_connects_delta\t%d\n", a["status.Aborted_connects"] - b["status.Aborted_connects"]
    printf "connection_refused_max_delta\t%d\n", a["status.Connection_errors_max_connections"] - b["status.Connection_errors_max_connections"]
    printf "threads_connected_after\t%s\n", a["status.Threads_connected"]
    printf "threads_running_after\t%s\n", a["status.Threads_running"]
    printf "max_used_connections_after\t%s\n", a["status.Max_used_connections"]
    printf "max_connections\t%s\n", a["variable.max_connections"]
  }' "${BEFORE}" "${AFTER}"
