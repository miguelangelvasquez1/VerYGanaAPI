// Autotest de la suite k6, sin red. Se corre con:
//   docker compose ... run --rm k6 run /scripts/selftest.js
// Sale con código distinto de 0 si algo no cuadra. No imprime usuarios ni tokens.
import { check } from 'k6';
import {
  RECAPTCHA_PLACEHOLDER, STAGES, STAFF, SYSTEM_TAGS, buildOptions, stagesForRole, vuMix, seededFor,
} from './lib/config.js';
import { checksum, buildEvent } from './lib/wompi.js';
import { loginPayload, registerConsumerPayload, registerCommercialPayload } from './lib/auth.js';
import { availableFor, identifierFor, indexFor } from './lib/users.js';
import { ENDPOINTS } from './lib/inventory.js';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { checks: ['rate==1'] },
};

// Vector de WompiWebhookSignatureVectorTest (Java): mismo evento y misma firma.
const VECTOR = {
  eventsKey: 'loadtest-wompi-events',
  timestamp: 1668097749,
  id: 'lt-tx-X1',
  status: 'APPROVED',
  amountInCents: 300000,
  checksum: '15028db4ebf404adbe270b9aab719cfc3493172e5ca911e32aca545d665b120e',
};

function sameMix(total, expected) {
  const m = vuMix(total);
  return ['consumer', 'commercial', 'designer', 'admin', 'compliance']
    .every((role) => m[role] === expected[role]);
}

export default function () {
  // 1. Vector de firma de Wompi.
  check(null, {
    'firma de Wompi igual al vector de Java':
      () => checksum(VECTOR.id, VECTOR.status, VECTOR.amountInCents, VECTOR.timestamp, VECTOR.eventsKey) === VECTOR.checksum,
    'el monto alterado cambia la firma':
      () => checksum(VECTOR.id, VECTOR.status, VECTOR.amountInCents + 1, VECTOR.timestamp, VECTOR.eventsKey) !== VECTOR.checksum,
  });
  const event = buildEvent({
    id: VECTOR.id, reference: 'R1', status: VECTOR.status, amountInCents: VECTOR.amountInCents,
    eventsKey: VECTOR.eventsKey, timestamp: VECTOR.timestamp,
  });
  const parsed = JSON.parse(event.body);
  check(null, {
    'el webhook lleva la firma del vector en cuerpo y header': () => event.checksum === VECTOR.checksum
      && parsed.signature.checksum === VECTOR.checksum
      && parsed.timestamp === VECTOR.timestamp
      && parsed.data.transaction.amount_in_cents === VECTOR.amountInCents
      && parsed.signature.properties.join(',')
        === 'transaction.id,transaction.status,transaction.amount_in_cents',
  });

  // 2. Mezcla de VUs (mismos números que LoadTestSeedPlanTest#vuMixFollowsSameRule).
  check(null, {
    '200 VUs -> 180/10/5/3/2': () => sameMix(200, { consumer: 180, commercial: 10, designer: 5, admin: 3, compliance: 2 }),
    '2000 VUs -> 1890/100/5/3/2': () => sameMix(2000, { consumer: 1890, commercial: 100, designer: 5, admin: 3, compliance: 2 }),
    'el personal interno es fijo en todos los escalones de B': () => STAGES.B.every(
      ([, total]) => total === 0 || (vuMix(total).designer === STAFF.designer
        && vuMix(total).admin === STAFF.admin && vuMix(total).compliance === STAFF.compliance)),
    'consumidores + comerciales + personal suman el total del escalón': () => STAGES.B.every(([, total]) => {
      if (total === 0) return true;
      const m = vuMix(total);
      return m.consumer + m.commercial + m.designer + m.admin + m.compliance === total;
    }),
  });

  // 3. Mapeo VU -> usuario sin colisiones (2.000 VUs en B, 200 en A).
  function noCollisions(role, scenario, vus) {
    const available = availableFor(role, scenario);
    const seen = new Set();
    for (let i = 0; i < vus; i++) {
      // vuId empieza en 1 y la iteración en 0, como k6.
      const index = indexFor(role, { vuId: i + 1, iteration: i });
      seen.add(identifierFor(role, index, available));
    }
    return seen.size === vus;
  }
  check(null, {
    'B: 1890 consumidores con usuario distinto': () => noCollisions('consumer', 'B', 1890),
    'B: 100 comerciales con usuario distinto': () => noCollisions('commercial', 'B', 100),
    'A: 180 consumidores con usuario distinto': () => noCollisions('consumer', 'A', 180),
    'A: 10 comerciales con usuario distinto': () => noCollisions('commercial', 'A', 10),
    'el personal interno (5/3/2) usa un usuario distinto por VU': () => ['designer', 'admin', 'compliance']
      .every((role) => noCollisions(role, 'B', STAFF[role])),
    'los ids de B alcanzan para todos los VUs sin envolver': () => seededFor('B').consumer >= 2000
      && seededFor('A').consumer >= 200,
  });

  // 4. Token de relleno de reCAPTCHA no vacío en login y registro.
  check(null, {
    'el login manda un recaptchaToken de relleno': () => loginPayload('x').recaptchaToken === RECAPTCHA_PLACEHOLDER
      && RECAPTCHA_PLACEHOLDER.length > 0,
    'el registro de consumidor manda un recaptchaToken de relleno': () => registerConsumerPayload(1).recaptchaToken
      === RECAPTCHA_PLACEHOLDER,
    'el registro de comercial manda un recaptchaToken de relleno': () => registerCommercialPayload(1).recaptchaToken
      === RECAPTCHA_PLACEHOLDER,
  });

  // 5. Etiquetas: url fuera en las opciones reales de cada escenario.
  const scenarios = ['smoke', 'A', 'B', 'B_CEILING'];
  check(null, {
    'systemTags no incluye url': () => SYSTEM_TAGS.indexOf('url') === -1
      && scenarios.every((s) => buildOptions(s).systemTags.indexOf('url') === -1),
    'A llega a 200 VUs y B a 2000 en total': () => {
      const peak = (s) => Math.max(...STAGES[s].map(([, t]) => t));
      return peak('A') === 200 && peak('B') === 2000 && peak('B_CEILING') === 2000;
    },
    'las etapas por rol de B suman los VUs del escalón': () => {
      const c = stagesForRole(STAGES.B, 'consumer');
      const m = stagesForRole(STAGES.B, 'commercial');
      return c.every((st, i) => st.target + m[i].target + STAFF.designer + STAFF.admin + STAFF.compliance
        === (STAGES.B[i][1] === 0 ? STAFF.designer + STAFF.admin + STAFF.compliance : STAGES.B[i][1]));
    },
  });

  // 6. Inventario cargado (cabecera y filas con recorrido o exclusión).
  check(null, {
    'el inventario tiene filas': () => ENDPOINTS.length > 400,
    'cada fila tiene recorrido o exclusión, no ambos': () => {
      for (let i = 0; i < ENDPOINTS.length; i += 1) {
        if ((ENDPOINTS[i].journey === '') === (ENDPOINTS[i].exclusion === '')) return false;
      }
      return true;
    },
  });
}
