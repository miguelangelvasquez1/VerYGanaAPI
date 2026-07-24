import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ─── Métricas personalizadas ──────────────────────────────────────────────────
const errorRate    = new Rate('error_rate');
const loginTrend   = new Trend('login_duration');
const levelsTrend  = new Trend('levels_duration');
const refTrend     = new Trend('referrals_duration');

// ─── Configuración de carga ───────────────────────────────────────────────────
export const options = {
  scenarios: {
    // ── Fase 1: Carga alta (150 VUs) ─────────────────────────────────────────
    login: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50  },  // sube a 50
        { duration: '1m',  target: 50  },  // mantiene 50
        { duration: '30s', target: 100 },  // sube a 100
        { duration: '1m',  target: 100 },  // mantiene 100
        { duration: '20s', target: 0   },  // baja
      ],
      gracefulRampDown: '10s',
      exec: 'testLogin',
    },
    levels: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50  },
        { duration: '1m',  target: 50  },
        { duration: '30s', target: 100 },
        { duration: '1m',  target: 100 },
        { duration: '20s', target: 0   },
      ],
      gracefulRampDown: '10s',
      exec: 'testLevels',
      startTime: '10s',
    },
    referrals: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50  },
        { duration: '1m',  target: 50  },
        { duration: '30s', target: 100 },
        { duration: '1m',  target: 100 },
        { duration: '20s', target: 0   },
      ],
      gracefulRampDown: '10s',
      exec: 'testReferrals',
      startTime: '10s',
    },

    // ── Fase 2: Spike test (300 VUs de golpe) ────────────────────────────────
    spike_levels: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 300 },  // sube a 300 en 10s (pico)
        { duration: '30s', target: 300 },  // mantiene el pico
        { duration: '10s', target: 0   },  // cae a 0
      ],
      gracefulRampDown: '5s',
      exec: 'testLevels',
      startTime: '3m30s',                  // empieza después de la carga alta
    },
    spike_referrals: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 300 },
        { duration: '30s', target: 300 },
        { duration: '10s', target: 0   },
      ],
      gracefulRampDown: '5s',
      exec: 'testReferrals',
      startTime: '3m30s',
    },
  },
  thresholds: {
    http_req_duration:  ['p(95)<1500'],   // más holgado para el spike
    error_rate:         ['rate<0.05'],    // toleramos hasta 5% en pico
    login_duration:     ['p(95)<1000'],
    levels_duration:    ['p(95)<800'],
    referrals_duration: ['p(95)<800'],
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
// Obtiene los tokens y los comparte con todos los escenarios.
// Así evitamos login masivo que dispara el AccountLockService.
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

  return { tokens };
}

function pickToken(data) {
  return data.tokens[Math.floor(Math.random() * data.tokens.length)].token;
}

// ─── Escenario 1: Login ───────────────────────────────────────────────────────
// Prueba el endpoint de login con credenciales válidas.
// Solo corre con UN usuario para no activar el lock.
export function testLogin(data) {
  group('Login', () => {
    const user = TEST_USERS[0];
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

    loginTrend.add(res.timings.duration);
    errorRate.add(res.status !== 200);

    check(res, {
      'login status 200':     (r) => r.status === 200,
      'retorna accessToken':  (r) => {
        try { return JSON.parse(r.body).accessToken !== undefined; }
        catch { return false; }
      },
      'responde en < 800ms':  (r) => r.timings.duration < 800,
    });

    sleep(2);
  });
}

// ─── Escenario 2: Sistema de niveles ─────────────────────────────────────────
export function testLevels(data) {
  group('Niveles', () => {
    const token = pickToken(data);
    const headers = {
      'Authorization': `Bearer ${token}`,
      'Content-Type':  'application/json',
    };

    // 2a. Perfil del nivel
    const profileRes = http.get(`${BASE_URL}/api/levels/me`, { headers });
    levelsTrend.add(profileRes.timings.duration);
    errorRate.add(profileRes.status !== 200);

    check(profileRes, {
      'levels/me — 200':          (r) => r.status === 200,
      'retorna currentLevel':     (r) => {
        try { return JSON.parse(r.body).currentLevel !== undefined; }
        catch { return false; }
      },
      'responde en < 500ms':      (r) => r.timings.duration < 500,
    });

    sleep(0.3);

    // 2b. Historial XP
    const historyRes = http.get(`${BASE_URL}/api/levels/me/history?page=0&size=10`, { headers });
    levelsTrend.add(historyRes.timings.duration);
    errorRate.add(historyRes.status !== 200);

    check(historyRes, {
      'levels/history — 200':     (r) => r.status === 200,
      'responde en < 500ms':      (r) => r.timings.duration < 500,
    });

    sleep(0.3);

    // 2c. Config de niveles (público)
    const configRes = http.get(`${BASE_URL}/api/levels/config`);
    levelsTrend.add(configRes.timings.duration);
    errorRate.add(configRes.status !== 200);

    check(configRes, {
      'levels/config — 200':      (r) => r.status === 200,
      'retorna 6 niveles':        (r) => {
        try { return JSON.parse(r.body).length === 6; }
        catch { return false; }
      },
      'responde en < 300ms':      (r) => r.timings.duration < 300,
    });

    sleep(1);
  });
}

// ─── Escenario 3: Sistema de referidos ───────────────────────────────────────
export function testReferrals(data) {
  group('Referidos', () => {
    const token = pickToken(data);
    const headers = {
      'Authorization': `Bearer ${token}`,
      'Content-Type':  'application/json',
    };

    // 3a. Código de referido
    const codeRes = http.get(`${BASE_URL}/referrals/my-code`, { headers });
    refTrend.add(codeRes.timings.duration);
    errorRate.add(codeRes.status !== 200);

    check(codeRes, {
      'referrals/my-code — 200':  (r) => r.status === 200,
      'retorna referralCode':     (r) => {
        try { return JSON.parse(r.body).referralCode !== undefined; }
        catch { return false; }
      },
      'responde en < 500ms':      (r) => r.timings.duration < 500,
    });

    sleep(0.5);

    // 3b. Lista de referidos
    const listRes = http.get(`${BASE_URL}/referrals/my-referrals`, { headers });
    refTrend.add(listRes.timings.duration);
    errorRate.add(listRes.status !== 200);

    check(listRes, {
      'referrals/my-referrals — 200': (r) => r.status === 200,
      'responde en < 500ms':          (r) => r.timings.duration < 500,
    });

    sleep(1);
  });
}
