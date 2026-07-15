# Security Monitoring — Resumen de cambios

## Archivos nuevos

```
security/auth/refreshToken/
  SecurityAuditService.java        → Publica eventos al sistema de auditoría existente (AuditLog)
  
security/monitoring/
  AlertSeverity.java               → Enum: LOW / MEDIUM / HIGH / CRITICAL
  SecurityAlertType.java           → Enum: BRUTE_FORCE, TOKEN_FARMING, SESSION_HIJACKING
  SecurityAlert.java               → DTO de alerta con tipo, severidad, fuente y datos extra
  BruteForcePattern.java           → Patrón: IP, intentos, ventana de tiempo, risk score
  TokenFarmingPattern.java         → Patrón: usuario, cantidad de tokens, IPs únicas
  SessionHijackingPattern.java     → Patrón: cambio de IP + user-agent en < 5 min

test/
  SecurityAuditServiceTest.java    →  5 tests unitarios
  SecurityMonitoringServiceTest.java → 12 tests unitarios
```

## Archivos modificados

| Archivo | Qué se cambió |
|---|---|
| `RefreshToken.java` | +campos `ipAddress`, `userAgent`, `lastUsedAt` · +índices DB · +`isActive()` · +`revoke()` |
| `RefreshTokenRepository.java` | +queries: `findAllActiveTokens`, `findByJti`, `deleteExpiredTokens`, `revokeAllByIpAddress`, etc. |
| `SecurityMonitoringService.java` | Descomentado y funcional |
| `TokenCleanupScheduler.java` | Descomentado y funcional |
| `TokenService.java` | Captura IP y User-Agent de cada request al guardar el refresh token |
| `AuthController.java` | Registra cada login fallido vía `SecurityAuditService.logAuthFailure` (category=AUTH) |
| `AuditLogRepository.java` | +queries: `findIpsWithFailedLoginsSince`, `findFailedLoginsByIpSince` |
| `application-dev.yml` | +bloque `app.security.*` con todos los parámetros |

## Qué detecta el sistema

| Amenaza | Cómo | Scheduler |
|---|---|---|
| Fuerza bruta / credential stuffing | IPs con ≥ 10 logins FALLIDOS/hora (audit_logs, category=AUTH, action=LOGIN_FAILED) — no cuenta éxitos, para no confundir tráfico legítimo de IPs compartidas (campus, oficina) con un ataque | Cada hora |
| Token farming | Usuario con > umbral de tokens creados en la ventana (checkWindowHours) | Cada hora |
| Session hijacking | Cambio de IP + UA en < 5 min | Cada hora |
| Sesiones excesivas | Usuario con ≥ max-per-user sesiones activas | Al conceder cada nuevo refresh token (TokenService), no por job |

## Flujo de una alerta

```
TokenCleanupScheduler (scheduled)
  └─► SecurityMonitoringService.analyzeSecurityPatterns()
        └─► detecta patrón
              └─► SecurityAuditService.logCriticalEvent / logSuspiciousActivity
                    └─► ApplicationEventPublisher → AuditEventListener → AuditLog (BD)
```

## Config principal (application-dev.yml)

```yaml
app:
  security:
    monitoring:
      enabled: true
      auto-block-enabled: false   # true = revoca tokens automáticamente
    suspicious-activity:
      failed-logins-per-ip-threshold: 10
      check-window-hours: 1
    sessions:
      max-per-user: 5
```
