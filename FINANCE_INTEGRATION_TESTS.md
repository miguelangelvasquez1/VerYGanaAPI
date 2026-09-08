# Pruebas de integración — Dominio Finance

Inventario completo de las pruebas de integración nuevas del dominio `finance`: `@WebMvcTest` con `SecurityConfig` real + JWT firmado contra `MockMvc`. Antes de este trabajo **ningún** controller de finance tenía test de integración. Resultado confirmado corriendo `mvn test` el 2026-08-27 junto con el resto del lote de finance: **395 pruebas totales del dominio, 0 fallos, 0 errores, 2 skipped (ver `FINANCE_REPOSITORY_TESTS.md`), BUILD SUCCESS**.

Todas las clases de este documento son **Nuevas**.

---

## `PlanChangeRequestControllerSecurityIntegrationTest` — cubre `PlanChangeRequestController` — 17 pruebas

`hasRole('COMMERCIAL')` a nivel de clase, verificado en los 6 endpoints (`GET /preview`, `POST /`, `GET /current`, `POST /{id}/cancel`, `POST /contract/{contractId}/approve`, `POST /{id}/top-up-checkout`): sin token → 401/403 y no llega al service; con rol CONSUMER → 403; con COMMERCIAL → 200. Todos ✅ Pasa.

## `PayoutAdminControllerSecurityIntegrationTest` — cubre `PayoutAdminController` — 11 pruebas

`hasRole('ADMIN')`, verificado en los 4 endpoints (`GET /`, `POST /run-now`, `POST /retry-now`, `GET /{id}/wompi-status`): sin token → denegado, no llega al service; con rol incorrecto → 403; con ADMIN → 200. Todos ✅ Pasa.

## `PayoutMethodAdminControllerSecurityIntegrationTest` — cubre `PayoutMethodAdminController` — 11 pruebas

`hasRole('ADMIN')`, verificado en los 4 endpoints (`GET /`, `POST /{id}/verify`, `POST /{id}/reject`, `GET /{id}/certificate`): trío sin-token/rol-incorrecto/ADMIN en `getByStatus`, `verify` y `reject`; sin-token/rol-incorrecto en `getCertificate` (streaming directo a `HttpServletResponse`, solo se verifica el límite de autorización). Todos ✅ Pasa.

## `TreasuryAdminControllerSecurityIntegrationTest` — cubre `TreasuryAdminController` — 10 pruebas

**Caso más importante del lote — autorización diferencial**: `/balance` y `/movements/{code}` son solo-ADMIN; `/config/keys-reserve-pct` permite tanto ADMIN como COMMERCIAL.

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getBalance_withoutToken_isDenied` | `/balance` sin token se deniega, no llega al service | ✅ Pasa |
| `getBalance_withCommercial_isForbidden` | `/balance` con COMMERCIAL se deniega (solo ADMIN) | ✅ Pasa |
| `getBalance_asAdmin_respondsOk` | `/balance` con ADMIN responde 200 | ✅ Pasa |
| `getMovements_withoutToken_isDenied` | `/movements/{code}` sin token se deniega | ✅ Pasa |
| `getMovements_withCommercial_isForbidden` | `/movements/{code}` con COMMERCIAL se deniega (solo ADMIN) | ✅ Pasa |
| `getMovements_asAdmin_respondsOk` | `/movements/{code}` con ADMIN responde 200 | ✅ Pasa |
| `getKeysReservePct_withoutToken_isDenied` | `/config/keys-reserve-pct` sin token se deniega | ✅ Pasa |
| `getKeysReservePct_withConsumer_isForbidden` | Con CONSUMER (ninguno de los 2 roles permitidos) se deniega | ✅ Pasa |
| `getKeysReservePct_asAdmin_respondsOk` | Con ADMIN responde 200 | ✅ Pasa |
| `getKeysReservePct_asCommercial_respondsOk` | Con COMMERCIAL TAMBIÉN responde 200 (override de `@PreAuthorize` distinto al resto de la clase) | ✅ Pasa |

## `PayoutMethodControllerSecurityIntegrationTest` — cubre `PayoutMethodController` — 21 pruebas

`hasRole('COMMERCIAL')` a nivel de clase. Trío completo sin-token/rol-incorrecto/COMMERCIAL en los 3 endpoints más sensibles (`create`, `verifyOtp`, `setDefault`); sin-token+COMMERCIAL en el resto (`getBanks`, `resendOtp`, `getAll`, `deactivate`, `prepareCertificateUpload`, `confirmCertificateUpload`). Todos ✅ Pasa.

## `WalletControllerSecurityIntegrationTest` — cubre `WalletController` — 7 pruebas

`hasRole('COMMERCIAL')`, trío completo en `/me/billing-summary`; sin-token+COMMERCIAL en `/me/deposits` y `/me/payouts`. Todos ✅ Pasa.

## `KeyWalletControllerSecurityIntegrationTest` — cubre `KeyWalletController` — 6 pruebas

`hasRole('CONSUMER')`, trío completo (sin token/rol incorrecto/CONSUMER) en `GET /balance` y `POST /spend`. Todos ✅ Pasa.

---

## Resumen

- **Clases de test**: 7 (todas nuevas — primera cobertura de integración de todo el dominio finance).
- **Pruebas individuales de esta categoría**: 83.
- **Total del dominio finance (unitarias + integración + repositorio)**: 395 pruebas, 0 fallos, 0 errores, 2 skipped — `mvn test`, 2026-08-27.
