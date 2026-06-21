# Security Monitoring — Resumen de cambios

## Archivos nuevos

```
security/auth/refreshToken/
  SecurityAuditService.java        → Publica eventos al sistema de auditoría existente (AuditLog)
  
security/monitoring/
  AlertSeverity.java               → Enum: LOW / MEDIUM / HIGH / CRITICAL
  SecurityAlertType.java           → Enum: BRUTE_FORCE, TOKEN_FARMING, GEOGRAPHIC_ANOMALY,
                                           DEVICE_FINGERPRINTING, SESSION_HIJACKING
  SecurityAlert.java               → DTO de alerta con tipo, severidad, fuente y datos extra
  BruteForcePattern.java           → Patrón: IP, intentos, ventana de tiempo, risk score
  TokenFarmingPattern.java         → Patrón: usuario, cantidad de tokens, IPs únicas
  GeographicAnomalyPattern.java    → Patrón: "viaje imposible" entre dos ubicaciones
  DeviceFingerprintingPattern.java → Patrón: device con muchos user-agents distintos
  SessionHijackingPattern.java     → Patrón: cambio de IP + user-agent en < 5 min

test/
  SecurityAuditServiceTest.java    →  5 tests unitarios
  SecurityMonitoringServiceTest.java → 12 tests unitarios
```

## Archivos modificados

| Archivo | Qué se cambió |
|---|---|
| `RefreshToken.java` | +campos `ipAddress`, `userAgent`, `deviceId`, `lastUsedAt` · +índices DB · +`isActive()` · +`revoke()` |
| `RefreshTokenRepository.java` | +queries: `findSuspiciousIPs`, `findAllActiveTokens`, `findByJti`, `deleteExpiredTokens`, `revokeAllByIpAddress`, etc. |
| `SecurityMonitoringService.java` | Descomentado y funcional |
| `TokenCleanupScheduler.java` | Descomentado y funcional |
| `TokenService.java` | Captura IP y User-Agent de cada request al guardar el refresh token |
| `application-dev.yml` | +bloque `app.security.*` con todos los parámetros |

## Qué detecta el sistema

| Amenaza | Cómo | Scheduler |
|---|---|---|
| Fuerza bruta | IPs con > 10 tokens/hora | Cada hora |
| Token farming | Usuario con > 10 tokens en 15 min | Cada hora |
| Anomalía geográfica | "Viaje imposible" entre IPs | Cada hora |
| Device fingerprinting | Device con > 5 user-agents distintos | Cada hora |
| Session hijacking | Cambio de IP + UA en < 5 min | Cada hora |
| Sesiones excesivas | Usuario con > 15 sesiones activas | Cada 30 min |

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
      tokens-per-ip-threshold: 10
      check-window-hours: 1
    sessions:
      max-per-user: 15
```
