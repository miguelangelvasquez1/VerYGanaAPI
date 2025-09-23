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
- Refresh token flow:
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody AuthRequest request) {
    // validar usuario y contraseña
    // generar access token
    // generar refresh token
    // guardar refresh token en BD o memoria
    return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken));
}

- Se usa: configuración de seguridad basada en recursos (Resource Server) de Spring Boot
- Usar swagger para pruebas
- Usar validators, mapper, dtos, excpetions personalizadas. --> Dejar la estructura con almenos una entidad., swagger,
- logs,
- Ver los preauthorize, ver donde mandar el accesstoken, ver lo que dijo claude
Mejoras Implementadas en tokens:

Índices optimizados para queries rápidas
Limpieza automática con schedulers
Tracking de sesiones detallado con IP, UserAgent, Device ID
Límite de sesiones por usuario (anti-spam)
Queries estadísticas para monitoreo y reportes
Detección de actividad sospechosa
