# Pruebas de integración — Dominio PQRS

Inventario completo de las pruebas de integración nuevas del dominio `PQRS`: `@WebMvcTest` con `SecurityConfig` real + JWT firmado contra `MockMvc`. Antes de este trabajo ningún controller de PQRS tenía test de integración (ambos ya tenían unitario). Resultado confirmado corriendo `mvn test` el 2026-08-27 junto con el resto del lote de PQRS: **45 pruebas nuevas, 0 fallos, 0 errores, BUILD SUCCESS**.

Todas las clases de este documento son **Nuevas**.

---

## `PqrsControllerSecurityIntegrationTest` — cubre `PqrsController` — 12 pruebas

`@PreAuthorize("isAuthenticated()")` a nivel de clase — sin restricción de rol específico, por lo que no hay caso "rol incorrecto", solo autenticado sí/no.

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `prepareAssetUpload_withoutToken_isDenied` | `POST /pqrs/assets/prepare-upload` sin token se deniega y no llega al service | ✅ Pasa |
| `prepareAssetUpload_authenticated_respondsOk` | Con usuario autenticado responde 200 | ✅ Pasa |
| `confirmAssetUpload_withoutToken_isDenied` | `POST /pqrs/assets/{id}/confirm` sin token se deniega | ✅ Pasa |
| `confirmAssetUpload_authenticated_respondsOk` | Con usuario autenticado responde 200 | ✅ Pasa |
| `streamAsset_withoutToken_isDenied` | `GET /pqrs/assets/{id}/view` sin token se deniega | ✅ Pasa |
| `streamAsset_authenticated_respondsOk` | Con usuario autenticado responde 200 (la autorización fina dueño-vs-admin vive en el service, mockeado) | ✅ Pasa |
| `createPqrs_withoutToken_isDenied` | `POST /pqrs` sin token se deniega | ✅ Pasa |
| `createPqrs_authenticated_respondsCreated` | Con usuario autenticado responde 201 | ✅ Pasa |
| `getMyPqrs_withoutToken_isDenied` | `GET /pqrs/mine` sin token se deniega | ✅ Pasa |
| `getMyPqrs_authenticated_respondsOk` | Con usuario autenticado responde 200 | ✅ Pasa |
| `getMyPqrsDetail_withoutToken_isDenied` | `GET /pqrs/{id}` sin token se deniega | ✅ Pasa |
| `getMyPqrsDetail_authenticated_respondsOk` | Con usuario autenticado responde 200 | ✅ Pasa |

## `PqrsAdminControllerSecurityIntegrationTest` — cubre `PqrsAdminController` — 13 pruebas

`hasRole('ADMIN')` a nivel de clase.

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getAssignedPqrs_withoutToken_isDenied` | `GET /admin/pqrs` sin token se deniega, no llega al service | ✅ Pasa |
| `getAssignedPqrs_withCommercial_isForbidden` | Con rol COMMERCIAL se deniega | ✅ Pasa |
| `getAssignedPqrs_withConsumer_isForbidden` | Con rol CONSUMER se deniega | ✅ Pasa |
| `getAssignedPqrs_asAdmin_respondsOk` | Con ADMIN autenticado responde 200 | ✅ Pasa |
| `getPqrsDetail_withoutToken_isDenied` | `GET /admin/pqrs/{id}` sin token se deniega | ✅ Pasa |
| `getPqrsDetail_withWrongRole_isForbidden` | Rol distinto a ADMIN se deniega | ✅ Pasa |
| `getPqrsDetail_asAdmin_respondsOk` | Con ADMIN responde 200 | ✅ Pasa |
| `markUnderReview_withoutToken_isDenied` | `PATCH /admin/pqrs/{id}/review` sin token se deniega | ✅ Pasa |
| `markUnderReview_withWrongRole_isForbidden` | Rol distinto a ADMIN se deniega | ✅ Pasa |
| `markUnderReview_asAdmin_respondsOk` | Con ADMIN responde 200 | ✅ Pasa |
| `respondToPqrs_withoutToken_isDenied` | `PATCH /admin/pqrs/{id}/respond` sin token se deniega | ✅ Pasa |
| `respondToPqrs_withWrongRole_isForbidden` | Rol distinto a ADMIN se deniega | ✅ Pasa |
| `respondToPqrs_asAdmin_respondsOk` | Con ADMIN responde 200 | ✅ Pasa |

---

## Resumen

- **Clases de test**: 2 (todas nuevas — primera cobertura de integración de todo el dominio PQRS).
- **Pruebas individuales de esta categoría**: 25.
- **Total del dominio PQRS de esta ronda**: 45 pruebas nuevas, 0 fallos, 0 errores — `mvn test`, 2026-08-27.
