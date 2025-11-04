## Dependencies:
- Spring web
- Spring Data JPA
- MySQL Connector
- Spring security
- oauth2 resource server
- configuration processor
- Lombok

## Observations:
- Implement Nimbus for JWT, implementar una clave separada para el refresh token, implementar redis para escalabilidad, accessToken en header
- El usuario ingresa su correo y clave, luego CustomUserDetailsService valida si coinciden con un usuario de la base de datos, si no coinciden lanza 401
- La clave privada se usa para firmar el token. La clave pública se usa para verificarlo.
- API de info Colombia: https://api-colombia.com
- Si se introducen refresh tokens, los self-signed JWTs pueden no ser lo mejor
- Article for JWTs: https://www.danvega.dev/blog/spring-security-jwt

- Se usa: configuración de seguridad basada en recursos (Resource Server) de Spring Boot
- Usar swagger para pruebas
- logs,
- Ver los preauthorize, ver mensajes de error para autenticaciones
- Implementar redis en vez de caché?
- Usar OffsetDateTime
- Añadir caché de usuarios?
- Mirar lo del cache de categorias
- https://www.datos.gov.co/api/v3/views/gdxc-w37w/export.csv?accessType=DOWNLOAD&app_token=bHWsGtRFRP9x8Hl8lYivqM1hQ -> Municipalitys and Departments

Mejoras Implementadas en tokens:

Índices optimizados para queries rápidas
Limpieza automática con schedulers
Tracking de sesiones detallado con IP, UserAgent, Device ID
Límite de sesiones por usuario (anti-spam)
Queries estadísticas para monitoreo y reportes
Detección de actividad sospechosa

CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    username VARCHAR(100),              -- usuario que hizo la acción
    action VARCHAR(50) NOT NULL,        -- CREATE, UPDATE, DELETE, LOGIN, etc.
    entity_name VARCHAR(100) NOT NULL,  -- nombre de la entidad (User, Ad, etc.)
    entity_id BIGINT,                   -- id de la entidad afectada
    old_values TEXT,                    -- valores antes (JSON)
    new_values TEXT,                    -- valores después (JSON)
    ip_address VARCHAR(50),             -- IP del cliente (opcional)
    user_agent VARCHAR(255),            -- navegador/cliente (opcional)
    status VARCHAR(20) DEFAULT 'SUCCESS', -- SUCCESS / FAILURE
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
