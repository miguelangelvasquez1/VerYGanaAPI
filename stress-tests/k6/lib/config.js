// Configuración de la prueba de carga: URL, mezcla de roles, escenarios y umbrales.
// Todo lo que cambia entre local y nube entra por variables de entorno (-e BASE_URL=...).
// Ningún dato personal sale de aquí: los usuarios se identifican por número (lib/users.js).

import { endpointThresholds } from './inventory.js';

export const BASE_URL = __ENV.BASE_URL || 'http://api:8080';

// Contraseña común de los usuarios sembrados (LOADTEST_PASSWORD). El valor por defecto es el
// ficticio de env/loadtest.local.env.example; en la nube se pasa con -e LOADTEST_PASSWORD.
export const PASSWORD = __ENV.LOADTEST_PASSWORD || 'LoadTest-Local-1!';

// Token de relleno para reCAPTCHA: no vale ante Google, pero el falso de loadtest lo
// acepta y el @NotBlank de los DTO se cumple.
export const RECAPTCHA_PLACEHOLDER = 'loadtest-placeholder';

// OTP fijo del falso de SMS (loadtest.stubs.otp-code).
export const OTP_CODE = __ENV.LOADTEST_OTP_CODE || '000000';

// Secreto de eventos de Wompi del perfil loadtest (literal ficticio de application-loadtest.yml).
export const WOMPI_EVENTS_KEY = __ENV.WOMPI_EVENTS_KEY || 'loadtest-wompi-events';

// Secreto del webhook de ZapSign del perfil loadtest.
export const ZAPSIGN_WEBHOOK_SECRET = __ENV.ZAPSIGN_WEBHOOK_SECRET || 'loadtest-zapsign-secret';

export const SCENARIO = __ENV.SCENARIO || 'smoke';

// Personal interno fijo en los dos escenarios.
export const STAFF = { designer: 5, admin: 3, compliance: 2 };
export const STAFF_TOTAL = STAFF.designer + STAFF.admin + STAFF.compliance;

// Usuarios sembrados por escenario (los mismos de LoadTestSeedPlan).
export const SEEDED = {
  A: { consumer: 940, commercial: 50 },
  B: { consumer: 9490, commercial: 500 },
};

// Redondeo half-up, como LoadTestSeedPlan.
function roundHalfUp(x) {
  return Math.floor(x + 0.5 + 1e-9);
}

/** Mezcla de VUs para un total dado: personal fijo y el resto 95/5. */
export function vuMix(total) {
  const rest = Math.max(total - STAFF_TOTAL, 0);
  const commercial = roundHalfUp(rest * 0.05);
  return {
    consumer: rest - commercial,
    commercial,
    designer: STAFF.designer,
    admin: STAFF.admin,
    compliance: STAFF.compliance,
  };
}

// Pausas entre pasos (think time) y entre sesiones. En smoke son mínimas.
export function thinkTime() {
  const [lo, hi] = SCENARIO === 'smoke' ? [0.05, 0.15] : [3, 10];
  return lo + Math.random() * (hi - lo);
}

export function sessionGap() {
  const [lo, hi] = SCENARIO === 'smoke' ? [0.1, 0.2] : [30, 60];
  return lo + Math.random() * (hi - lo);
}

// ---------------------------------------------------------------------------------------------
// Etapas. Cada etapa es [duración en segundos, VUs totales al final de la etapa].
// ---------------------------------------------------------------------------------------------

const MIN = 60;

export const STAGES = {
  // A: 0 -> 200 en 5 min, sostiene 20 min, baja en 2 min (27 min).
  A: [
    [5 * MIN, 200],
    [20 * MIN, 200],
    [2 * MIN, 0],
  ],
  // B: escalones 500 -> 1.000 -> 1.500 -> 2.000 (3 min de subida + 3 de meseta), 20 min a 2.000
  // y 3 min de bajada (unos 47 min).
  B: [
    [3 * MIN, 500], [3 * MIN, 500],
    [3 * MIN, 1000], [3 * MIN, 1000],
    [3 * MIN, 1500], [3 * MIN, 1500],
    [3 * MIN, 2000], [3 * MIN, 2000],
    [20 * MIN, 2000],
    [3 * MIN, 0],
  ],
  // B_CEILING: los mismos escalones, sin la meseta larga (solo fase nube).
  B_CEILING: [
    [3 * MIN, 500], [3 * MIN, 500],
    [3 * MIN, 1000], [3 * MIN, 1000],
    [3 * MIN, 1500], [3 * MIN, 1500],
    [3 * MIN, 2000], [3 * MIN, 2000],
    [3 * MIN, 0],
  ],
};

// DURATION_SCALE acorta las etapas para una corrida corta de depuración (p. ej. 0.1 = 10 % de la duración;
// los VUs de cada etapa no cambian). No lo usa ninguna corrida aceptada: por defecto vale 1.
const DURATION_SCALE = Number(__ENV.DURATION_SCALE || 1);
if (DURATION_SCALE !== 1) {
  Object.keys(STAGES).forEach((name) => {
    STAGES[name] = STAGES[name].map(([seconds, vus]) => [Math.max(Math.round(seconds * DURATION_SCALE), 5), vus]);
  });
}

/** Duración total en segundos de una lista de etapas. */
export function totalSeconds(stages) {
  return stages.reduce((acc, [d]) => acc + d, 0);
}

/**
 * Etapas de un rol con VUs variables (consumidor y comercial): la meta de cada etapa es la parte
 * de esa meta total que le toca según vuMix().
 */
export function stagesForRole(stages, role) {
  return stages.map(([seconds, total]) => ({
    duration: `${seconds}s`,
    target: vuMix(total)[role],
  }));
}

/** Escenario "A" -> semilla A; "B" y "B_CEILING" -> semilla B. */
export function seededFor(scenario) {
  return scenario === 'A' ? SEEDED.A : SEEDED.B;
}

/** Umbrales de aceptación de una instancia. */
export const THRESHOLDS = {
  'http_req_duration{expected_response:true}': ['p(95)<1000'],
  http_req_failed: ['rate<0.01'],
};

// Etiquetas del sistema: las de k6 por defecto, sin `url` (lleva datos de la ruta).
export const SYSTEM_TAGS = [
  'proto', 'subproto', 'status', 'method', 'name', 'group', 'check',
  'error', 'error_code', 'tls_version', 'scenario', 'service', 'expected_response',
];

/**
 * Opciones de k6 para un escenario (smoke | A | B | B_CEILING). Los nombres de las funciones de
 * `exec` son los que exporta main.js.
 */
export function buildOptions(scenario) {
  const base = {
    discardResponseBodies: true,
    systemTags: SYSTEM_TAGS,
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
    // Las submétricas por endpoint solo se declaran al leer las opciones (__VU === 0): los VUs no
    // las usan y construirlas 2.000 veces gastaría memoria.
    thresholds: __VU === 0 ? Object.assign({}, THRESHOLDS, endpointThresholds()) : THRESHOLDS,
  };

  if (scenario === 'smoke') {
    // 1 VU por rol y 1 iteración que recorre todos los subrecorridos (cobertura del inventario).
    const one = (exec) => ({ executor: 'per-vu-iterations', vus: 1, iterations: 1, maxDuration: '30m', exec });
    const all = {
      consumer: one('consumerSmoke'),
      commercial: one('commercialSmoke'),
      designer: one('designerSmoke'),
      admin: one('adminSmoke'),
      compliance: one('complianceSmoke'),
    };
    // ONLY=commercial,admin limita el smoke a esos roles (depurar un recorrido sin correr los demás).
    const only = (__ENV.ONLY || '').split(',').map((r) => r.trim()).filter((r) => r !== '');
    const scenarios = {};
    Object.keys(all).forEach((role) => {
      if (only.length === 0 || only.indexOf(role) !== -1) {
        scenarios[role] = all[role];
      }
    });
    return Object.assign(base, { scenarios });
  }

  const stages = STAGES[scenario];
  if (!stages) {
    throw new Error(`SCENARIO desconocido: ${scenario} (smoke | A | B | B_CEILING)`);
  }
  const maxSeconds = totalSeconds(stages);
  const ramping = (exec, role) => ({
    executor: 'ramping-vus',
    startVUs: 0,
    stages: stagesForRole(stages, role),
    gracefulRampDown: '60s',
    gracefulStop: '60s',
    exec,
  });
  // Personal interno: todos activos desde el primer segundo, un VU por usuario.
  const staff = (exec, count) => ({
    executor: 'per-vu-iterations',
    vus: count,
    iterations: 1,
    maxDuration: `${maxSeconds + 180}s`,
    exec,
  });
  return Object.assign(base, {
    scenarios: {
      consumer: ramping('consumerSession', 'consumer'),
      commercial: ramping('commercialSession', 'commercial'),
      designer: staff('designerLoop', STAFF.designer),
      admin: staff('adminLoop', STAFF.admin),
      compliance: staff('complianceLoop', STAFF.compliance),
    },
  });
}
