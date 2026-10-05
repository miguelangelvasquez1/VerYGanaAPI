-- Usuarios locales (contraseñas ficticias). Corre una sola vez, como root, sobre un volumen vacío.
-- verygana_app: todo sobre el esquema y nada global (sin SUPER, SYSTEM_VARIABLES_ADMIN ni SET_USER_ID).
CREATE USER IF NOT EXISTS 'verygana_app'@'%' IDENTIFIED BY 'lt-app-local';
GRANT ALL PRIVILEGES ON `verygana`.* TO 'verygana_app'@'%';

-- verygana_monitor: para los scripts de db/ (estado del servidor y performance_schema).
CREATE USER IF NOT EXISTS 'verygana_monitor'@'%' IDENTIFIED BY 'lt-monitor-local';
GRANT PROCESS ON *.* TO 'verygana_monitor'@'%';
GRANT SELECT ON `performance_schema`.* TO 'verygana_monitor'@'%';
-- Para que check-schema.sql vea las tablas y los triggers del esquema (information_schema solo
-- muestra lo que el usuario puede ver; TRIGGER es necesario para listar triggers).
GRANT SELECT, TRIGGER ON `verygana`.* TO 'verygana_monitor'@'%';
