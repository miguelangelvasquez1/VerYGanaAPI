-- ============================================================================
-- tls-check.sql · las conexiones de la app van cifradas (en local, sslMode=REQUIRED)
--
-- Solo lectura sobre performance_schema.threads. Con la API arriba (pool abierto), todas las
-- conexiones de verygana_app tienen que ser SSL/TLS: la fila "sin_tls" tiene que dar 0.
-- En la nube el usuario de la app tiene otro nombre: cambiar @lt_app_user antes de correr.
-- ============================================================================

SET @lt_app_user = 'verygana_app';

SELECT PROCESSLIST_USER AS user, CONNECTION_TYPE AS connection_type, COUNT(*) AS conexiones
FROM performance_schema.threads
WHERE PROCESSLIST_USER = @lt_app_user
GROUP BY PROCESSLIST_USER, CONNECTION_TYPE;

SELECT COUNT(*) AS conexiones_app,
       SUM(CONNECTION_TYPE = 'SSL/TLS') AS con_tls,
       SUM(CONNECTION_TYPE IS NULL OR CONNECTION_TYPE <> 'SSL/TLS') AS sin_tls
FROM performance_schema.threads
WHERE PROCESSLIST_USER = @lt_app_user;
