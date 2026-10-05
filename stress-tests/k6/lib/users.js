// VU -> usuario sembrado, determinista y sin colisiones.
//
// Los correos son los del sembrado (db/loadtest/01-users.sql): lt-<rol>-<n>@loadtest.invalid.
// Nunca se imprimen: los VUs se identifican por número en cualquier mensaje.
//
// Cómo se reparte (verificado con k6 1.3.0, ver selftest.js):
//  - personal interno: escenarios per-vu-iterations de 1 iteración por VU; en ellos
//    scenario.iterationInInstance es denso (0..n-1) y único, así que cada VU toma un usuario distinto;
//  - consumidores: vu.idInInstance es único en toda la prueba y no pasa del total de VUs (200 en A,
//    2.000 en B), siempre menor que los 940 / 9.490 consumidores sembrados: no hay colisión;
//  - comerciales: igual que los consumidores, (vu.idInInstance - 1) % comerciales sembrados. El id es
//    único en toda la prueba pero no denso por escenario (los VUs de los demás roles lo intercalan),
//    así que unos pocos comerciales comparten cuenta (colisión aceptada): sin consecuencia
//    funcional más allá de compartir sesión y datos. scenario.iterationInInstance no sirve: cuenta las
//    iteraciones de todos los VUs del escenario y en B pasa de 500 antes de que lleguen los últimos.
import exec from 'k6/execution';
import { SCENARIO, STAFF, seededFor } from './config.js';

const PREFIX = {
  consumer: 'consumer',
  commercial: 'commercial',
  designer: 'designer',
  admin: 'admin',
  compliance: 'compliance',
};

/** Correo sembrado de un rol; `index` es de base 0 y se envuelve sobre los usuarios disponibles. */
export function identifierFor(role, index, available) {
  const n = (index % available) + 1;
  return `lt-${PREFIX[role]}-${n}@loadtest.invalid`;
}

/** Número de usuario (base 1) que corresponde a un índice; sirve para los datos únicos por VU. */
export function userNumber(index, available) {
  return (index % available) + 1;
}

/** Usuarios sembrados de un rol en el escenario dado. */
export function availableFor(role, scenario) {
  if (role === 'consumer' || role === 'commercial') {
    return seededFor(scenario)[role];
  }
  return STAFF[role];
}

/** Índice (base 0) de un VU según la regla de arriba; `ctx` = { vuId, iteration } para poder probarlo. */
export function indexFor(role, ctx) {
  return role === 'consumer' || role === 'commercial' ? ctx.vuId - 1 : ctx.iteration;
}

let claimed = null;

/** Índice del VU actual dentro de su rol (se reclama una vez y se conserva mientras el VU viva). */
export function claimIndex(role) {
  if (claimed === null) {
    claimed = indexFor(role, {
      vuId: exec.vu.idInInstance,
      iteration: exec.scenario.iterationInInstance,
    });
  }
  return claimed;
}

/** Correo sembrado del VU actual. En smoke (1 VU por rol) es siempre el primero. */
export function myIdentifier(role) {
  const index = SCENARIO === 'smoke' ? 0 : claimIndex(role);
  return identifierFor(role, index, availableFor(role, SCENARIO));
}

