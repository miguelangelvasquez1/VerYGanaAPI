import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ─── Métricas personalizadas ──────────────────────────────────────────────────
const errorRate       = new Rate('error_rate');
const livesTrend       = new Trend('raffles_lives_duration');
const activesTrend     = new Trend('raffles_actives_duration');
const detailTrend      = new Trend('raffle_detail_duration');
const drawStatusTrend  = new Trend('raffle_draw_status_duration');
const meTrend          = new Trend('raffles_me_duration');

// ─── Configuración de carga ───────────────────────────────────────────────────
// Extiende stress-test.js con los endpoints de rifas. Se mantiene en un archivo
// separado para poder correrlo (y ajustar sus thresholds) independientemente
// del resto de la API.
export const options = {
  scenarios: {
    // ── Chequeo único de seguridad: /api/raffles/me SIN token ───────────────
    // No es un test de carga: corre 1 sola vez para dejar registrado si el
    // endpoint responde 401 limpio o revienta con 500 (falta @PreAuthorize +
    // PublicPaths permite /api/raffles/** completo).
    security_check: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '10s',
      exec: 'testUnauthenticatedMe',
      startTime: '0s',
    },

    // ── Lecturas públicas de alto tráfico (home / listados) ──────────────────
    raffles_lives: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '1m', target: 100 },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '10s',
      exec: 'testLiveRaffles',
      startTime: '5s',
    },
    raffles_actives: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '1m', target: 100 },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '10s',
      exec: 'testActiveRaffles',
      startTime: '15s',
    },
    raffle_detail: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '1m', target: 100 },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '10s',
      exec: 'testRaffleDetail',
      startTime: '15s',
    },

    // ── Sala de espera: polling de draw-status antes de un sorteo ────────────
    // Es el patrón más pesado en producción: cientos de usuarios refrescando
    // el conteo regresivo en los minutos previos al sorteo en vivo.
    raffle_draw_status: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 50 },
        { duration: '1m', target: 150 },
        { duration: '1m30s', target: 150 },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '10s',
      exec: 'testDrawStatus',
      startTime: '25s',
    },
    // Pico repentino de la sala de espera (ej. notificación push "¡ya casi
    // empieza el sorteo!" que hace que todos entren a la vez).
    spike_draw_status: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 300 },
        { duration: '30s', target: 300 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
      exec: 'testDrawStatus',
      startTime: '3m30s',
    },

    // ── "Mis rifas" autenticado ────────────────────────────────────────────
    raffles_me: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '1m', target: 100 },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '10s',
      exec: 'testMyRaffles',
      startTime: '15s',
    },
  },
  thresholds: {
    http_req_duration:            ['p(95)<1500'],
    error_rate:                   ['rate<0.05'],
    raffles_lives_duration:       ['p(95)<800'],
    raffles_actives_duration:     ['p(95)<800'],
    raffle_detail_duration:       ['p(95)<800'],
    raffle_draw_status_duration:  ['p(95)<600'],   // es polling frecuente, debe ser rápido
    raffles_me_duration:          ['p(95)<800'],
  },
};

const BASE_URL = 'http://localhost:8080';

const TEST_USERS = [
  { email: 'consumer@verygana.com',   password: '123456' },
  { email: 'bronce@verygana.com',     password: '123456' },
  { email: 'plata@verygana.com',      password: '123456' },
  { email: 'oro@verygana.com',        password: '123456' },
  { email: 'rubi@verygana.com',       password: '123456' },
  { email: 'esmeralda@verygana.com',  password: '123456' },
  { email: 'diamante@verygana.com',   password: '123456' },
];

// ─── Setup: se ejecuta UNA sola vez antes de todos los VUs ───────────────────
export function setup() {
  const tokens = [];

  for (const user of TEST_USERS) {
    const res = http.post(
      `${BASE_URL}/auth/login`,
      JSON.stringify({ identifier: user.email, password: user.password }),
      {
        headers: {
          'Content-Type': 'application/json',
          'X-Client-Type': 'mobile',
        },
      }
    );

    if (res.status === 200) {
      const body = JSON.parse(res.body);
      tokens.push({ email: user.email, token: body.accessToken });
      console.log(`✅ Token obtenido para ${user.email}`);
    } else {
      console.error(`❌ Login fallido para ${user.email}: ${res.status} — ${res.body}`);
    }
  }

  if (tokens.length === 0) {
    throw new Error('No se pudo obtener ningún token. Verifica que los usuarios existan y no estén bloqueados.');
  }

  // ID de rifa a usar en /raffle_detail y /draw-status.
  // 1) __ENV.RAFFLE_ID si se pasó explícitamente (k6 run -e RAFFLE_ID=42 ...)
  // 2) si no, se autodescubre pidiendo una rifa ACTIVE/LIVE real al backend
  let raffleId = __ENV.RAFFLE_ID ? Number(__ENV.RAFFLE_ID) : null;

  if (!raffleId) {
    const headers = { Authorization: `Bearer ${tokens[0].token}` };

    const livesRes = http.get(`${BASE_URL}/api/raffles/lives`, { headers });
    if (livesRes.status === 200) {
      const lives = JSON.parse(livesRes.body);
      if (lives.length > 0) raffleId = lives[0].id;
    }

    if (!raffleId) {
      const activesRes = http.get(`${BASE_URL}/api/raffles/actives?pageNumber=0`, { headers });
      if (activesRes.status === 200) {
        const actives = JSON.parse(activesRes.body);
        if (actives.data && actives.data.length > 0) raffleId = actives.data[0].id;
      }
    }
  }

  if (!raffleId) {
    console.error(
      '⚠️  No se encontró ninguna rifa LIVE/ACTIVE para probar /raffle/{id} y /draw-status. ' +
      'Crea una rifa activa en el entorno de pruebas o pasa -e RAFFLE_ID=<id>. ' +
      'Esos dos escenarios van a fallar con 404 hasta que exista una.'
    );
  } else {
    console.log(`ℹ️  Usando raffleId=${raffleId} para /raffle/{id} y /draw-status`);
  }

  return { tokens, raffleId };
}

function pickToken(data) {
  return data.tokens[Math.floor(Math.random() * data.tokens.length)].token;
}

// ─── Chequeo único de seguridad ───────────────────────────────────────────────
export function testUnauthenticatedMe() {
  group('Seguridad: /api/raffles/me sin token', () => {
    const meRes = http.get(`${BASE_URL}/api/raffles/me?status=ACTIVE`);
    const countRes = http.get(`${BASE_URL}/api/raffles/me/count?status=ACTIVE`);

    console.log(`GET /api/raffles/me sin token -> ${meRes.status}`);
    console.log(`GET /api/raffles/me/count sin token -> ${countRes.status}`);

    check(meRes, {
      'me sin token: NO responde 500 (debería ser 401/403)': (r) => r.status !== 500,
    });
    check(countRes, {
      'me/count sin token: NO responde 500 (debería ser 401/403)': (r) => r.status !== 500,
    });
  });
}

// ─── Escenario: rifas en vivo (home) ─────────────────────────────────────────
export function testLiveRaffles() {
  group('Rifas — lives', () => {
    const res = http.get(`${BASE_URL}/api/raffles/lives`);
    livesTrend.add(res.timings.duration);
    errorRate.add(res.status !== 200);

    check(res, {
      'raffles/lives — 200': (r) => r.status === 200,
      'responde en < 500ms': (r) => r.timings.duration < 500,
    });

    sleep(1);
  });
}

// ─── Escenario: rifas activas (listado paginado) ─────────────────────────────
export function testActiveRaffles() {
  group('Rifas — actives', () => {
    const res = http.get(`${BASE_URL}/api/raffles/actives?pageNumber=0`);
    activesTrend.add(res.timings.duration);
    errorRate.add(res.status !== 200);

    check(res, {
      'raffles/actives — 200': (r) => r.status === 200,
      'responde en < 500ms': (r) => r.timings.duration < 500,
    });

    sleep(1);
  });
}

// ─── Escenario: detalle de una rifa ──────────────────────────────────────────
export function testRaffleDetail(data) {
  if (!data.raffleId) return;

  group('Rifas — detalle', () => {
    const res = http.get(`${BASE_URL}/api/raffles/${data.raffleId}`);
    detailTrend.add(res.timings.duration);
    errorRate.add(res.status !== 200);

    check(res, {
      'raffles/{id} — 200': (r) => r.status === 200,
      'responde en < 500ms': (r) => r.timings.duration < 500,
    });

    sleep(1);
  });
}

// ─── Escenario: sala de espera / draw-status (polling) ───────────────────────
export function testDrawStatus(data) {
  if (!data.raffleId) return;

  group('Rifas — draw-status (waiting room)', () => {
    const res = http.get(`${BASE_URL}/api/raffles/${data.raffleId}/draw-status`);
    drawStatusTrend.add(res.timings.duration);
    errorRate.add(res.status !== 200);

    check(res, {
      'draw-status — 200': (r) => r.status === 200,
      'trae viewerCount':  (r) => {
        try { return JSON.parse(r.body).viewerCount !== undefined; }
        catch { return false; }
      },
      'responde en < 400ms': (r) => r.timings.duration < 400,
    });

    // Simula el refresco periódico real del cliente en la sala de espera.
    sleep(2);
  });
}

// ─── Escenario: "mis rifas" autenticado ──────────────────────────────────────
export function testMyRaffles(data) {
  group('Rifas — me', () => {
    const token = pickToken(data);
    const headers = { Authorization: `Bearer ${token}` };

    const meRes = http.get(`${BASE_URL}/api/raffles/me?status=ACTIVE`, { headers });
    meTrend.add(meRes.timings.duration);
    errorRate.add(meRes.status !== 200);

    check(meRes, {
      'raffles/me — 200': (r) => r.status === 200,
      'responde en < 500ms': (r) => r.timings.duration < 500,
    });

    sleep(0.5);

    const countRes = http.get(`${BASE_URL}/api/raffles/me/count?status=ACTIVE`, { headers });
    meTrend.add(countRes.timings.duration);
    errorRate.add(countRes.status !== 200);

    check(countRes, {
      'raffles/me/count — 200': (r) => r.status === 200,
      'responde en < 500ms':    (r) => r.timings.duration < 500,
    });

    sleep(1);
  });
}
