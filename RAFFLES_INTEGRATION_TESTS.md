# Pruebas de integración — Dominio Raffles

Inventario completo de pruebas de integración del dominio `raffles`: `@WebMvcTest` con `SecurityConfig` real + JWT firmado contra `MockMvc`, y el `@DataJpaTest` que ejercita el flujo completo de emisión de tickets bajo concurrencia. Resultado confirmado corriendo `mvn test` el 2026-08-26 junto con el resto del dominio: **309 pruebas totales del dominio, 0 fallos, 0 errores, BUILD SUCCESS**.

**Nuevo** = creada en esta sesión. **Existente** = ya estaba en el repo.

---

## `RaffleControllerSecurityIntegrationTest` — cubre `RaffleController` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `listRafflesDraftFilter_withoutToken_isDenied` | `GET /api/raffles?status=DRAFT` sin token se deniega y no llega al service | ✅ Pasa |
| `getRaffleById_draftRaffle_withoutToken_doesNotExposeData` | `GET /api/raffles/{id}` de una rifa DRAFT sin token no expone sus datos | ✅ Pasa |
| `meWithoutToken_respondsUnauthorizedNotServerError` | `GET /api/raffles/me` sin Authorization responde 401/403, no 500 | ✅ Pasa |
| `meCountWithoutToken_respondsUnauthorizedNotServerError` | `GET /api/raffles/me/count` sin Authorization responde 401/403, no 500 | ✅ Pasa |

## `RaffleResultControllerSecurityIntegrationTest` — cubre `RaffleResultController` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getRaffleResultByRaffleId_withoutToken_respondsOk` | `GET /results/raffle/{id}` sin token responde 200 (endpoint público), no 401 | ✅ Pasa |
| `getLastRaffleResults_withoutToken_respondsOk` | `GET /results/last` sin token responde 200, no 401 | ✅ Pasa |
| `getDrawProofByRaffleId_withoutToken_respondsOk` | `GET /results/raffle/{id}/draw-proof` sin token responde 200, no 401 | ✅ Pasa |

## `RaffleWinnerControllerSecurityIntegrationTest` — cubre `RaffleWinnerController` — Existente

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getLastWinners_withoutToken_respondsOk` | `GET /winners/last` sin token responde 200 (debe ser público) | ✅ Pasa |
| `getWonPrizes_withoutToken_isDenied` | `GET /winners/my-prizes` sin token se deniega | ✅ Pasa |
| `getWonPrizes_asConsumer_respondsOk` | `GET /winners/my-prizes` con CONSUMER autenticado responde 200 paginado | ✅ Pasa |
| `claimPrize_withoutToken_isDenied` | `POST /winners/claim` sin token se deniega | ✅ Pasa |

## `UserRaffleTicketControllerSecurityIntegrationTest` — cubre `UserRaffleTicketController` — **Nuevo**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `getUserTicketsByRaffle_withoutToken_isDenied` | `GET /raffle-tickets/raffle/{id}` sin token se deniega y no llega al service | ✅ Pasa |
| `getUserTicketsByRaffle_withWrongRole_isForbidden` | Con rol distinto a CONSUMER se deniega (403) | ✅ Pasa |
| `getUserTicketsByRaffle_asConsumer_respondsOk` | Con CONSUMER autenticado responde 200 | ✅ Pasa |
| `getUserWinnerTickets_withoutToken_isDenied` | `GET /raffle-tickets/winners` sin token se deniega y no llega al service | ✅ Pasa |
| `getUserWinnerTickets_asConsumer_respondsOk` | Con CONSUMER autenticado responde 200 | ✅ Pasa |
| `getWinnerUserTotalTickets_withoutToken_isDenied` | `GET /raffle-tickets/winners/balance` sin token se deniega | ✅ Pasa |
| `getWinnerUserTotalTickets_withWrongRole_isForbidden` | Con rol distinto a CONSUMER se deniega (403) | ✅ Pasa |
| `getWinnerUserTotalTickets_asConsumer_respondsOk` | Con CONSUMER autenticado responde 200 | ✅ Pasa |

## `RaffleAdminControllerSecurityIntegrationTest` — cubre `RaffleAdminController` — **Nuevo**

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `countRafflesByStatus_withoutToken_isDenied` | `GET /admin/raffles/count` sin token se deniega, no llega al service | ✅ Pasa |
| `countRafflesByStatus_withWrongRole_isForbidden` | Con rol distinto a ADMIN se deniega (403) | ✅ Pasa |
| `countRafflesByStatus_asAdmin_respondsOk` | Con ADMIN autenticado responde 200 | ✅ Pasa |
| `getRaffleStats_withoutToken_isDenied` | `GET /admin/raffles/{id}/stats` sin token se deniega | ✅ Pasa |
| `getRaffleStats_asAdmin_respondsOk` | Con ADMIN autenticado responde 200 | ✅ Pasa |
| `activateRaffle_withoutToken_isDenied` | `PATCH /admin/raffles/{id}/activate` sin token se deniega | ✅ Pasa |
| `activateRaffle_withWrongRole_isForbidden` | Con rol distinto a ADMIN se deniega (403) | ✅ Pasa |
| `activateRaffle_asAdmin_respondsNoContent` | Con ADMIN autenticado responde 204 | ✅ Pasa |
| `deleteRaffle_withoutToken_isDenied` | `DELETE /admin/raffles/{id}` sin token se deniega | ✅ Pasa |
| `deleteRaffle_asAdmin_respondsNoContent` | Con ADMIN autenticado responde 204 | ✅ Pasa |
| `confirmRaffleCreation_withoutToken_isDenied` | `POST /admin/raffles/confirm` sin token se deniega, no llega al service | ✅ Pasa |

> Nota de diseño: `POST /confirm` no incluye el caso "rol incorrecto → 403" porque `@PreAuthorize` (AOP) se evalúa después de que Spring MVC valida el `@RequestBody`; con cuerpo inválido la petición recibe 400 antes de llegar a la verificación de rol. Ese caso ya queda cubierto por `/count` y `/activate` en este mismo archivo.

## `RaffleTicketConcurrencyIntegrationTest` — cubre el flujo `RaffleTicketServiceImpl` + `RaffleRepository` + repos reales bajo concurrencia — Existente

`@DataJpaTest` con H2 en memoria (`MODE=MySQL`), importa los beans de servicio y mappers MapStruct reales (no mocks a ese nivel).

| Prueba (método) | Propósito | Resultado |
|---|---|---|
| `concurrentIssuance_usesExactlyTheAvailableCap` | 20 usuarios piden 1 ticket a la vez sobre una rifa con solo 5 disponibles: se usan exactamente los 5 y el resto falla con `LimitReachedException` (valida el lock pesimista de `findByIdForUpdate` bajo carga real) | ✅ Pasa |

---

## Fuera de alcance (documentado, no un gap pendiente)

`RaffleDrawWebSocketController` usa `@MessageMapping` sobre STOMP/WebSocket, no HTTP — `@WebMvcTest`/`MockMvc` no aplica y el proyecto no tiene infraestructura de test STOMP. Ya cuenta con test unitario (ver `RAFFLES_UNIT_TESTS.md`); no se agregó integración para no introducir un mecanismo de test nuevo sin decisión explícita del equipo.

## Resumen

- **Clases de test**: 6 (5 `@WebMvcTest` + 1 `@DataJpaTest` de concurrencia).
- **Pruebas individuales de esta categoría**: 27.
- **Total del dominio raffles (unitarias + integración + repositorio)**: 309 pruebas, 0 fallos, 0 errores (`mvn test`, 2026-08-26).
