import http from 'k6/http';
import encoding from 'k6/encoding';
import exec from 'k6/execution';
import { check, sleep, group, fail } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

/**
 * Prueba de carga de la lógica de mascotas.
 *
 * Cubre los 35 endpoints que algún cliente real llama hoy: el build de Unity
 * (/pet/**), el panel del comercial, el del diseñador y el del admin. Quedan
 * FUERA a propósito los 9 que no llama nadie — el CRUD de catálogo y el de
 * notificaciones de /game-designer/pet, y el alias muerto POST /pet/scenes
 * (el juego pide /pet/scenes-objects).
 *
 * ── Requisitos ──────────────────────────────────────────────────────────────
 *
 * 1. TOKENS. Este script NO hace login: /auth/login verifica reCAPTCHA v3 y no
 *    hay bandera para desactivarlo, así que un token de captcha no se puede
 *    scriptear (es de un solo uso y caduca en 2 minutos). Hay que sacar los JWT
 *    del navegador ya logueado (DevTools → Application → Local Storage, o la
 *    respuesta de /auth/login en la pestaña Network) y pasarlos con -e.
 *    Los tokens que falten simplemente apagan sus escenarios.
 *
 * 2. El comercial necesita plan PREMIUM: CAN_HAVE_PETS no está en BASIC ni en
 *    STANDARD, y sin esa capacidad /image, POST / y las dos de /metrics
 *    responden 400. comercial@verygana.com queda en STANDARD tras el seed.
 *
 * 3. Los dos endpoints de URL pre-firmada (/commercial/pet/requests/image y
 *    /game-designer/pet/assets) necesitan credenciales de R2 configuradas. La
 *    firma se calcula en el backend, sin salir a la red — se está midiendo el
 *    HMAC, no a Cloudflare.
 *
 * ── Uso ─────────────────────────────────────────────────────────────────────
 *
 *   k6 run stress-tests/stress-test-pets.js \
 *     -e CONSUMER_TOKEN=... -e COMMERCIAL_TOKEN=... \
 *     -e DESIGNER_TOKEN=...  -e ADMIN_TOKEN=...
 *
 * Variables opcionales:
 *   BASE_URL   destino (por defecto http://localhost:8080)
 *   SESIONES   sesiones de juego que se abren en setup (por defecto 10)
 *   SMOKE=1    una pasada corta de 1 VU por escenario, para verificar el montaje
 *   PET_SESSION  "token:hash" de una sesión ya abierta, para medir la capa del
 *                juego sin token de consumidor (esas rutas son públicas)
 *   CICLO=1    activa el escenario de ciclo de vida (ver abajo)
 *   CICLOS     iteraciones de ese escenario (por defecto 6)
 *
 * ── Qué escribe en la base ──────────────────────────────────────────────────
 *
 * El escenario `escrituras` crea escenas y comentarios. DELETE de escena es
 * borrado lógico (active=false), así que las filas quedan; no las ve el juego
 * (getAllScenes filtra por active) pero sí engordan el panel del diseñador.
 *
 * `ciclo` (opt-in) es el único que toca la máquina de estados: sube una imagen
 * real a R2, crea la solicitud, y la lleva hasta COMPLETED o REJECTED. Va aparte
 * y con pocas iteraciones porque esas transiciones son de un solo sentido —
 * aprobar dos veces la misma solicitud no es carga, es un 4xx. Publicar además
 * inserta un ítem en el catálogo del juego.
 *
 * Limpieza al terminar (ver también el resumen que imprime teardown):
 *   DELETE FROM pet_scene_objects WHERE scene_id IN
 *     (SELECT id FROM pet_scenes WHERE scene_id BETWEEN 9000 AND 9999);
 *   DELETE FROM pet_scenes WHERE scene_id BETWEEN 9000 AND 9999;
 *   DELETE FROM catalog_request_comments WHERE content LIKE '[k6]%';
 *   DELETE FROM pet_catalog_items WHERE name LIKE '[k6]%';
 */

// ─── Métricas por capa ────────────────────────────────────────────────────────
const errorRate      = new Rate('error_rate');
const juegoTrend     = new Trend('juego_duration');
const guardadoTrend  = new Trend('guardado_duration');
const sesionTrend    = new Trend('sesion_duration');
const comercialTrend = new Trend('comercial_duration');
const disenadorTrend = new Trend('disenador_duration');
const adminTrend     = new Trend('admin_duration');
const escrituraTrend = new Trend('escritura_duration');
const cicloTrend     = new Trend('ciclo_duration');
const cicloOk        = new Counter('ciclo_completados');

/**
 * Un contador por código de error. Sin esto, el reporte dice "25% de fallos" y no
 * de qué: 401 (token vencido), 422 (sesión de juego expirada) y 500 se ven igual,
 * y son diagnósticos opuestos.
 */
const FALLOS = {
  400: new Counter('fallos_400_peticion_invalida'),
  401: new Counter('fallos_401_sin_token'),
  403: new Counter('fallos_403_sin_permiso'),
  404: new Counter('fallos_404_no_encontrado'),
  409: new Counter('fallos_409_conflicto'),
  422: new Counter('fallos_422_sesion_expirada'),
  500: new Counter('fallos_500_error_servidor'),
};
const fallosOtros = new Counter('fallos_otros');

// ─── Configuración ────────────────────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const TOKENS = {
  consumidor: __ENV.CONSUMER_TOKEN   || '',
  comercial:  __ENV.COMMERCIAL_TOKEN || '',
  disenador:  __ENV.DESIGNER_TOKEN   || '',
  admin:      __ENV.ADMIN_TOKEN      || '',
};

const N_SESIONES = Number(__ENV.SESIONES || 10);

/**
 * Credenciales de una sesión ya existente, como "token:hash". Sirve para medir
 * la capa del juego sin token de consumidor: /pet/catalog, /pet/scenes-objects
 * y /pet/notifications son públicas y solo validan la sesión.
 */
const SESION_FIJA = __ENV.PET_SESSION || '';
const N_CICLOS   = Number(__ENV.CICLOS   || 6);
const CON_CICLO  = __ENV.CICLO === '1';

const JSON_H = { 'Content-Type': 'application/json' };

/** PNG de 1×1 transparente. R2 valida los bytes reales, no el content-type declarado. */
const PNG_1X1 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';

/** Blob de guardado con tamaño parecido al real: el backend lo trata como texto opaco. */
const SAVE_BLOB = JSON.stringify({
  version: 3,
  pet: { name: 'k6', hunger: 70, thirst: 55, hygiene: 40, humor: 80, energy: 65, bodyFat: 12 },
  inventory: Array.from({ length: 40 }, (_, i) => ({ itemId: 1000 + i, qty: (i % 5) + 1 })),
  scenes: { current: 1, unlocked: [-1, 0, 1, 2] },
});

// ─── Escenarios ───────────────────────────────────────────────────────────────
// Se arman según los tokens disponibles: sin token de un rol, su escenario no
// existe, en vez de correr y llenar el reporte de 401.

function rampa(pico, startTime) {
  const mitad = Math.max(1, Math.round(pico / 2));
  return {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: mitad },
      { duration: '1m',  target: mitad },
      { duration: '30s', target: pico  },
      { duration: '1m',  target: pico  },
      { duration: '20s', target: 0     },
    ],
    gracefulRampDown: '10s',
    startTime,
  };
}

const escenarios = {};

if (TOKENS.consumidor || SESION_FIJA) {
  // La capa del juego es la que de verdad recibe tráfico: un consumidor jugando
  // pide catálogo, escenas y notificaciones en cada arranque de partida.
  escenarios.juego = { ...rampa(100, '0s'), exec: 'testJuego' };
  escenarios.spike_juego = {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '10s', target: 300 },
      { duration: '30s', target: 300 },
      { duration: '10s', target: 0   },
    ],
    gracefulRampDown: '5s',
    exec: 'testJuego',
    startTime: '3m30s',
  };
}

if (TOKENS.consumidor) {
  // Estas dos sí necesitan el JWT: el guardado es del consumidor, no de la partida.
  escenarios.guardado = { ...rampa(30, '10s'), exec: 'testGuardado' };
  escenarios.sesiones = {
    executor: 'constant-arrival-rate',
    rate: 5, timeUnit: '1s', duration: '2m',
    preAllocatedVUs: 10, maxVUs: 40,
    exec: 'testSesion', startTime: '20s',
  };
}

if (TOKENS.comercial) escenarios.panel_comercial = { ...rampa(20, '10s'), exec: 'testComercial' };
if (TOKENS.disenador) escenarios.panel_disenador = { ...rampa(20, '10s'), exec: 'testDisenador' };
if (TOKENS.admin)     escenarios.panel_admin     = { ...rampa(20, '10s'), exec: 'testAdmin' };

if (TOKENS.comercial || TOKENS.disenador || TOKENS.admin) {
  // Pocos VUs a propósito: son escrituras y el objetivo es ver su latencia bajo
  // la carga de lectura de los otros escenarios, no saturarlas por su cuenta.
  escenarios.escrituras = {
    executor: 'constant-vus', vus: 3, duration: '3m',
    exec: 'testEscrituras', startTime: '20s',
  };
}

if (CON_CICLO && TOKENS.comercial && TOKENS.disenador && TOKENS.admin) {
  escenarios.ciclo = {
    executor: 'shared-iterations',
    vus: 2, iterations: N_CICLOS, maxDuration: '5m',
    exec: 'testCiclo', startTime: '30s',
  };
}

/**
 * SMOKE=1: una pasada de 1 VU por escenario, sin rampas ni pico. Sirve para
 * verificar tokens, permisos y forma de las respuestas antes de soltar la carga
 * de verdad — descubrir con 300 VUs que al comercial le falta el plan PREMIUM
 * cuesta cinco minutos y un reporte inservible.
 */
if (__ENV.SMOKE === '1') {
  delete escenarios.spike_juego;
  Object.keys(escenarios).forEach((nombre) => {
    if (nombre === 'ciclo') {
      escenarios.ciclo = { ...escenarios.ciclo, vus: 1, iterations: 2, startTime: '0s' };
      return;
    }
    escenarios[nombre] = {
      executor: 'constant-vus',
      vus: 1,
      duration: '15s',
      exec: escenarios[nombre].exec,
    };
  });
}

export const options = {
  scenarios: escenarios,
  thresholds: {
    error_rate:         ['rate<0.05'],
    juego_duration:     ['p(95)<800'],
    guardado_duration:  ['p(95)<600'],
    sesion_duration:    ['p(95)<800'],
    comercial_duration: ['p(95)<1000'],
    disenador_duration: ['p(95)<1000'],
    admin_duration:     ['p(95)<1000'],
    escritura_duration: ['p(95)<1500'],
    http_req_duration:  ['p(95)<2000'],
  },
};

// ─── Utilidades ───────────────────────────────────────────────────────────────

/**
 * Sesión de juego que usa este VU. Se cachea para poder renovarla al vencer:
 * elegir una al azar del pool en cada iteración haría que la renovación se
 * perdiera a la siguiente vuelta.
 */
let sesionVU = null;
let avisoSesion = false;

function sesionDeVU(data) {
  if (!sesionVU) sesionVU = elegir(data.sesiones);
  return sesionVU;
}

/**
 * Abre una sesión nueva para este VU. Solo se puede con token de consumidor: en
 * modo PET_SESSION no hay con qué pedirla, así que se avisa una vez y el reporte
 * queda marcado con fallos_422_sesion_expirada.
 */
function renovarSesion(data) {
  if (!TOKENS.consumidor) {
    if (!avisoSesion) {
      avisoSesion = true;
      console.error(
        '❌ La sesión de PET_SESSION venció (duran 30 min). Sin CONSUMER_TOKEN no se ' +
        'puede renovar: lo que se mida a partir de acá es el rechazo, no el endpoint.'
      );
    }
    return;
  }
  const res = http.post(`${BASE_URL}/pet/session/init`, null, { headers: auth(TOKENS.consumidor) });
  if (res.status === 200) {
    const cred = credencialesDe((cuerpo(res) || {}).url);
    if (cred.sessionToken) sesionVU = cred;
  }
}

/** Tope de errores impresos por VU. */
const MAX_LOGS = 5;
let logsEmitidos = 0;

function auth(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

function elegir(lista) {
  if (!lista || lista.length === 0) return null;
  return lista[Math.floor(Math.random() * lista.length)];
}

function cuerpoSesion(s) {
  return JSON.stringify({ session_token: s.sessionToken, user_hash: s.userHash });
}

/** Saca session_token y user_hash de la URL que devuelve /pet/session/init. */
function credencialesDe(url) {
  const query = (url || '').split('?')[1] || '';
  const p = {};
  query.split('&').forEach((kv) => {
    const [k, v] = kv.split('=');
    if (k) p[k] = decodeURIComponent(v || '');
  });
  return { sessionToken: p.session_token, userHash: p.user_hash };
}

function cuerpo(res) {
  try { return JSON.parse(res.body); } catch (e) { return null; }
}

/**
 * Registra una respuesta: alimenta la tendencia de su capa, la tasa de error y
 * los checks. `esperado` es la lista de códigos que cuentan como éxito — GET
 * /pet/save devuelve 204 cuando el jugador todavía no guardó nada, y eso no es
 * un fallo.
 */
function medir(res, trend, etiqueta, esperado, validaCuerpo) {
  const codigos = esperado || [200];
  const ok = codigos.indexOf(res.status) !== -1;

  trend.add(res.timings.duration);
  errorRate.add(!ok);

  const checks = {};
  checks[`${etiqueta} → ${codigos.join('/')}`] = () => ok;
  if (validaCuerpo) {
    checks[`${etiqueta} → cuerpo esperado`] = () => {
      const b = cuerpo(res);
      return ok && b !== null && validaCuerpo(b);
    };
  }
  check(res, checks);

  if (!ok) {
    (FALLOS[res.status] || fallosOtros).add(1);
    // Con tope: bajo 300 VUs un console.error por iteración tapa el reporte y
    // falsea los tiempos. Pero el tope es por VU y global al test, no por las
    // primeras iteraciones — si algo se rompe en el minuto 3 hay que verlo.
    if (logsEmitidos < MAX_LOGS) {
      logsEmitidos++;
      console.error(`❌ ${etiqueta} → ${res.status} ${String(res.body).slice(0, 160)}`);
    }
  }
  return ok;
}

// ─── Setup ────────────────────────────────────────────────────────────────────
// Abre las sesiones de juego y descubre los ids con los que trabajan los
// escenarios. Todo lo que no se pueda descubrir apaga su bloque, no el test.

export function setup() {
  const sinToken = Object.keys(TOKENS).filter((k) => !TOKENS[k]);

  if (sinToken.length === 4 && !SESION_FIJA) {
    fail(
      'No hay ningún token.\n' +
      'Este script no puede loguearse: /auth/login exige reCAPTCHA v3 y el token es de un solo uso.\n' +
      'Saca los JWT del navegador ya logueado y pásalos así:\n' +
      '  k6 run stress-tests/stress-test-pets.js -e CONSUMER_TOKEN=... -e COMMERCIAL_TOKEN=... -e DESIGNER_TOKEN=... -e ADMIN_TOKEN=...'
    );
  }
  if (sinToken.length > 0) {
    console.warn(`⚠️  Sin token de: ${sinToken.join(', ')} — se omiten sus escenarios.`);
  }

  const data = {
    sesiones: [],
    notificaciones: [],
    solicitudesComercial: [],
    solicitudesDisenador: [],
    borradoresDisenador: [],
    solicitudesAdmin: [],
    designerUserId: null,
  };

  if (SESION_FIJA) {
    const [sessionToken, userHash] = SESION_FIJA.split(':');
    data.sesiones.push({ sessionToken, userHash });
    console.log('✅ usando la sesión de juego pasada en PET_SESSION');
  }

  if (TOKENS.consumidor) {
    for (let i = 0; i < N_SESIONES; i++) {
      const res = http.post(`${BASE_URL}/pet/session/init`, null, { headers: auth(TOKENS.consumidor) });
      if (res.status !== 200) {
        console.error(`❌ /pet/session/init → ${res.status} ${String(res.body).slice(0, 200)}`);
        break;
      }
      const cred = credencialesDe((cuerpo(res) || {}).url);
      if (cred.sessionToken) data.sesiones.push(cred);
    }
    console.log(`✅ ${data.sesiones.length} sesiones de juego abiertas`);
  }

  // Las sesiones caducan a los 30 min (games.session-expiration-minutes). Este
  // test dura ~5, pero si se alarga hay que volver a abrirlas o /pet/** dará 404.
  if (data.sesiones.length > 0) {
    const notifs = http.post(`${BASE_URL}/pet/notifications`, cuerpoSesion(data.sesiones[0]), { headers: JSON_H });
    if (notifs.status === 200) {
      data.notificaciones = ((cuerpo(notifs) || {}).notifications || []).map((n) => n.id);
    }
    console.log(`✅ ${data.notificaciones.length} notificaciones para marcar como leídas`);
  }

  if (TOKENS.comercial) {
    const res = http.get(`${BASE_URL}/commercial/pet/requests`, { headers: auth(TOKENS.comercial) });
    if (res.status === 200) data.solicitudesComercial = (cuerpo(res) || []).map((r) => r.id);
    else console.error(`❌ GET /commercial/pet/requests → ${res.status} ${String(res.body).slice(0, 200)}`);
    console.log(`✅ ${data.solicitudesComercial.length} solicitudes del comercial`);
  }

  if (TOKENS.disenador) {
    const res = http.get(`${BASE_URL}/game-designer/pet/requests`, { headers: auth(TOKENS.disenador) });
    if (res.status === 200) {
      const lista = cuerpo(res) || [];
      data.solicitudesDisenador = lista.map((r) => r.id);
      // El borrador solo se puede guardar después de aprobar: en PENDING o
      // IN_REVIEW el service responde 409/500, no carga útil.
      data.borradoresDisenador = lista
        .filter((r) => r.status === 'APPROVED' || r.status === 'ITEM_IN_PROGRESS')
        .map((r) => r.id);
    }
    console.log(
      `✅ ${data.solicitudesDisenador.length} solicitudes asignadas al diseñador ` +
      `(${data.borradoresDisenador.length} en estado de borrador)`
    );
  }

  if (TOKENS.admin) {
    const res = http.get(`${BASE_URL}/api/admin/pet-requests`, { headers: auth(TOKENS.admin) });
    if (res.status === 200) data.solicitudesAdmin = (cuerpo(res) || []).map((r) => r.id);

    const dis = http.get(`${BASE_URL}/api/admin/pet-requests/designers`, { headers: auth(TOKENS.admin) });
    if (dis.status === 200) {
      const activos = cuerpo(dis) || [];
      if (activos.length > 0) data.designerUserId = activos[0].userId;
    }
    console.log(
      `✅ ${data.solicitudesAdmin.length} solicitudes visibles al admin, ` +
      `diseñador activo userId=${data.designerUserId}`
    );
  }

  if (CON_CICLO && !data.designerUserId) {
    console.warn('⚠️  CICLO=1 pero no hay diseñador activo: aprobar y reasignar van a fallar.');
  }

  return data;
}

// ─── Capa juego: pública, autenticada con session_token ───────────────────────
// POST y no GET porque WebGL descarta el body de los GET (ver el comentario en
// PetGameConfigController). Sin JWT: estas rutas están en PublicPaths.

export function testJuego(data) {
  const s = sesionDeVU(data);
  if (!s) return;
  const body = cuerpoSesion(s);

  group('Juego', () => {
    const cat = http.post(`${BASE_URL}/pet/catalog`, body, { headers: JSON_H });

    // 404 = sesión inexistente, 422 = expirada. Las sesiones duran 30 minutos
    // (games.session-expiration-minutes) y una corrida larga las entierra a
    // todas: a partir de ahí se estaría midiendo el camino de rechazo, que es
    // más rápido que el real y deja el p95 mintiendo hacia abajo. Se renueva y
    // se reintenta la iteración en la siguiente pasada.
    if (cat.status === 404 || cat.status === 422) {
      renovarSesion(data);
      medir(cat, juegoTrend, 'POST /pet/catalog', [200], (b) => Array.isArray(b.foods));
      return;
    }

    medir(cat, juegoTrend, 'POST /pet/catalog', [200], (b) => Array.isArray(b.foods));
    sleep(0.3);

    // El alias que usa el build. /pet/scenes es el mismo handler y no lo llama nadie.
    medir(
      http.post(`${BASE_URL}/pet/scenes-objects`, body, { headers: JSON_H }),
      juegoTrend, 'POST /pet/scenes-objects', [200],
      (b) => Array.isArray(b.scenes)
    );
    sleep(0.3);

    medir(
      http.post(`${BASE_URL}/pet/notifications`, body, { headers: JSON_H }),
      juegoTrend, 'POST /pet/notifications', [200],
      (b) => Array.isArray(b.notifications)
    );
    sleep(1);
  });
}

// ─── Capa juego: progreso del jugador (JWT del consumidor) ────────────────────
// Ojo con la lectura de este escenario: todos los VUs comparten un consumidor,
// así que el PUT pelea por la misma fila de pet_player_saves. La contención que
// se vea acá es peor que la real, donde cada jugador escribe la suya.

export function testGuardado(data) {
  const h = auth(TOKENS.consumidor);

  group('Guardado', () => {
    medir(
      http.get(`${BASE_URL}/pet/save`, { headers: h }),
      guardadoTrend, 'GET /pet/save', [200, 204]
    );
    sleep(0.3);

    medir(
      http.put(`${BASE_URL}/pet/save`, JSON.stringify({ data: SAVE_BLOB }), { headers: h }),
      guardadoTrend, 'PUT /pet/save', [200]
    );
    sleep(0.3);

    const id = elegir(data.notificaciones);
    const s = sesionDeVU(data);
    if (id && s) {
      medir(
        http.patch(`${BASE_URL}/pet/notifications/${id}/read`, cuerpoSesion(s), { headers: JSON_H }),
        guardadoTrend, 'PATCH /pet/notifications/{id}/read', [200]
      );
    }
    sleep(1);
  });
}

// ─── Apertura de sesión ───────────────────────────────────────────────────────
// A tasa constante y no en rampa: cada llamada inserta una fila en pet_sessions
// y no hay purga, así que conviene saber exactamente cuántas se crearon.

export function testSesion() {
  const res = http.post(`${BASE_URL}/pet/session/init`, null, { headers: auth(TOKENS.consumidor) });
  medir(res, sesionTrend, 'POST /pet/session/init', [200], (b) => typeof b.url === 'string');
}

// ─── Panel del comercial ──────────────────────────────────────────────────────

export function testComercial(data) {
  const h = auth(TOKENS.comercial);

  group('Panel comercial', () => {
    medir(
      http.get(`${BASE_URL}/commercial/pet/requests`, { headers: h }),
      comercialTrend, 'GET /commercial/pet/requests', [200], (b) => Array.isArray(b)
    );
    sleep(0.3);

    medir(
      http.get(`${BASE_URL}/commercial/pet/requests/metrics`, { headers: h }),
      comercialTrend, 'GET /commercial/pet/requests/metrics', [200]
    );
    sleep(0.3);

    // Sin rango son los últimos 30 días, que es como lo pide la gráfica.
    medir(
      http.get(`${BASE_URL}/commercial/pet/requests/metrics/daily`, { headers: h }),
      comercialTrend, 'GET /commercial/pet/requests/metrics/daily', [200]
    );
    sleep(0.3);

    const id = elegir(data.solicitudesComercial);
    if (id) {
      medir(
        http.get(`${BASE_URL}/commercial/pet/requests/${id}/comments`, { headers: h }),
        comercialTrend, 'GET /commercial/pet/requests/{id}/comments', [200]
      );
    }
    sleep(1);
  });
}

// ─── Panel del diseñador ──────────────────────────────────────────────────────

export function testDisenador(data) {
  const h = auth(TOKENS.disenador);

  group('Panel diseñador', () => {
    medir(
      http.get(`${BASE_URL}/game-designer/pet/scenes`, { headers: h }),
      disenadorTrend, 'GET /game-designer/pet/scenes', [200], (b) => Array.isArray(b)
    );
    sleep(0.3);

    medir(
      http.get(`${BASE_URL}/game-designer/pet/scenes/canvas`, { headers: h }),
      disenadorTrend, 'GET /game-designer/pet/scenes/canvas', [200],
      (b) => b.width === 1920 && b.height === 891
    );
    sleep(0.3);

    medir(
      http.get(`${BASE_URL}/game-designer/pet/requests`, { headers: h }),
      disenadorTrend, 'GET /game-designer/pet/requests', [200], (b) => Array.isArray(b)
    );
    sleep(0.3);

    const id = elegir(data.solicitudesDisenador);
    if (id) {
      medir(
        http.get(`${BASE_URL}/game-designer/pet/requests/${id}`, { headers: h }),
        disenadorTrend, 'GET /game-designer/pet/requests/{id}', [200]
      );
      sleep(0.3);
      medir(
        http.get(`${BASE_URL}/game-designer/pet/requests/${id}/comments`, { headers: h }),
        disenadorTrend, 'GET /game-designer/pet/requests/{id}/comments', [200]
      );
    }
    sleep(1);
  });
}

// ─── Panel del admin ──────────────────────────────────────────────────────────

export function testAdmin(data) {
  const h = auth(TOKENS.admin);

  group('Panel admin', () => {
    medir(
      http.get(`${BASE_URL}/api/admin/pet-requests`, { headers: h }),
      adminTrend, 'GET /api/admin/pet-requests', [200], (b) => Array.isArray(b)
    );
    sleep(0.3);

    // Con filtro pega a otra consulta (getRequestsByStatus), por eso va aparte.
    medir(
      http.get(`${BASE_URL}/api/admin/pet-requests?status=PENDING`, { headers: h }),
      adminTrend, 'GET /api/admin/pet-requests?status', [200]
    );
    sleep(0.3);

    medir(
      http.get(`${BASE_URL}/api/admin/pet-requests/designers`, { headers: h }),
      adminTrend, 'GET /api/admin/pet-requests/designers', [200], (b) => Array.isArray(b)
    );
    sleep(0.3);

    const id = elegir(data.solicitudesAdmin);
    if (id) {
      medir(
        http.get(`${BASE_URL}/api/admin/pet-requests/${id}`, { headers: h }),
        adminTrend, 'GET /api/admin/pet-requests/{id}', [200]
      );
      sleep(0.3);
      medir(
        http.get(`${BASE_URL}/api/admin/pet-requests/${id}/comments`, { headers: h }),
        adminTrend, 'GET /api/admin/pet-requests/{id}/comments', [200]
      );
    }
    sleep(1);
  });
}

// ─── Escrituras ───────────────────────────────────────────────────────────────
// Lo que se puede repetir sin romper nada: firmar subidas, el ciclo completo de
// una escena (que se limpia sola) y comentarios, que son append-only.

export function testEscrituras(data) {
  const marca = `${exec.vu.idInTest}-${exec.scenario.iterationInTest}`;

  group('Escrituras', () => {
    if (TOKENS.comercial) {
      // Solo firma la URL: no sube nada a R2 ni crea la solicitud.
      medir(
        http.post(`${BASE_URL}/commercial/pet/requests/image`, JSON.stringify({
          contentType: 'image/png',
          originalFileName: `k6-${marca}.png`,
          sizeBytes: 2048,
        }), { headers: auth(TOKENS.comercial) }),
        escrituraTrend, 'POST /commercial/pet/requests/image', [200],
        (b) => typeof b.uploadUrl === 'string'
      );
      sleep(0.3);
    }

    if (TOKENS.disenador) {
      medir(
        http.post(`${BASE_URL}/game-designer/pet/assets`, JSON.stringify({
          kind: 'SCENE_OBJECT',
          contentType: 'image/png',
          originalFileName: `k6-${marca}.png`,
          sizeBytes: 2048,
        }), { headers: auth(TOKENS.disenador) }),
        escrituraTrend, 'POST /game-designer/pet/assets', [200],
        (b) => typeof b.uploadUrl === 'string'
      );
      sleep(0.3);

      cicloDeEscena(marca);

      const idBorrador = elegir(data.borradoresDisenador);
      if (idBorrador) {
        // Sobrescribe el borrador completo: es idempotente, se puede repetir.
        medir(
          http.patch(`${BASE_URL}/game-designer/pet/requests/${idBorrador}/draft`, JSON.stringify({
            externalId: 1000 + (exec.vu.idInTest % 500),
            name: `[k6] borrador ${marca}`,
            price: 25,
            description: 'Borrador generado por la prueba de carga',
            hungerDelta: 10,
          }), { headers: auth(TOKENS.disenador) }),
          escrituraTrend, 'PATCH /game-designer/pet/requests/{id}/draft', [200]
        );
        sleep(0.3);
      }
    }

    comentar(data, marca);
    sleep(1);
  });
}

/**
 * Crear → actualizar → borrar una escena. Es el único bloque de escritura que se
 * limpia solo, aunque a medias: DELETE es lógico, así que la fila queda inactiva.
 * Usa sceneId 9000+ para no pisar los del juego (-1, 0, 1, 2).
 */
function cicloDeEscena(marca) {
  const h = auth(TOKENS.disenador);
  const escena = (sufijo) => JSON.stringify({
    sceneId: 9000 + (exec.vu.idInTest % 900),
    active: true,
    objects: [{
      objectId: `k6-${marca}-${sufijo}`,
      type: 'image',
      objectKey: 'scene-objects/k6/placeholder.png',
      x: 100, y: 200, width: 128, height: 128, scaleMultiplier: 1.0,
    }],
  });

  const creada = http.post(`${BASE_URL}/game-designer/pet/scenes`, escena('a'), { headers: h });
  if (!medir(creada, escrituraTrend, 'POST /game-designer/pet/scenes', [201], (b) => b.id > 0)) return;

  const id = (cuerpo(creada) || {}).id;
  sleep(0.3);

  medir(
    http.put(`${BASE_URL}/game-designer/pet/scenes/${id}`, escena('b'), { headers: h }),
    escrituraTrend, 'PUT /game-designer/pet/scenes/{id}', [200]
  );
  sleep(0.3);

  medir(
    http.del(`${BASE_URL}/game-designer/pet/scenes/${id}`, null, { headers: h }),
    escrituraTrend, 'DELETE /game-designer/pet/scenes/{id}', [204]
  );
}

/** Un comentario por rol disponible, cada uno en un hilo al que ese rol tenga acceso. */
function comentar(data, marca) {
  const hilos = [
    [TOKENS.comercial, elegir(data.solicitudesComercial), '/commercial/pet/requests', comercialTrend],
    [TOKENS.disenador, elegir(data.solicitudesDisenador), '/game-designer/pet/requests', disenadorTrend],
    [TOKENS.admin,     elegir(data.solicitudesAdmin),     '/api/admin/pet-requests', adminTrend],
  ];

  hilos.forEach(([token, id, base]) => {
    if (!token || !id) return;
    medir(
      http.post(`${BASE_URL}${base}/${id}/comments`,
        JSON.stringify({ content: `[k6] carga ${marca}` }),
        { headers: auth(token) }),
      escrituraTrend, `POST ${base}/{id}/comments`, [201]
    );
    sleep(0.2);
  });
}

// ─── Ciclo de vida completo (opt-in con CICLO=1) ──────────────────────────────
// Las transiciones son de un solo sentido: cada iteración necesita una solicitud
// nueva, así que esto mide latencia por paso, no throughput. La mitad de las
// iteraciones termina publicando y la otra mitad rechazando, para cubrir ambos
// finales de la máquina de estados.

export function testCiclo(data) {
  const n = exec.scenario.iterationInTest;
  const marca = `${exec.vu.idInTest}-${n}`;
  const hCom = auth(TOKENS.comercial);
  const hAdm = auth(TOKENS.admin);
  const hDis = auth(TOKENS.disenador);

  group('Ciclo de solicitud', () => {
    // 1. Firmar y subir la imagen. Sin objeto real en R2, submit falla: valida
    //    contra el bucket que exista y que los bytes sean de verdad una imagen.
    const permiso = http.post(`${BASE_URL}/commercial/pet/requests/image`, JSON.stringify({
      contentType: 'image/png',
      originalFileName: `k6-ciclo-${marca}.png`,
      sizeBytes: 70,
    }), { headers: hCom });
    if (!medir(permiso, cicloTrend, 'ciclo · POST /commercial/pet/requests/image', [200])) return;

    const { objectKey, uploadUrl } = cuerpo(permiso) || {};
    const subida = http.put(uploadUrl, encoding.b64decode(PNG_1X1, 'std', 'b'), {
      headers: { 'Content-Type': 'image/png' },
    });
    if (!check(subida, { 'ciclo · PUT a R2 → 200': (r) => r.status === 200 })) {
      console.error(`❌ subida a R2 → ${subida.status}`);
      return;
    }

    // 2. El comercial crea la solicitud (consume presupuesto del plan).
    const creada = http.post(`${BASE_URL}/commercial/pet/requests`, JSON.stringify({
      productName: `[k6] producto ${marca}`,
      description: 'Solicitud generada por la prueba de carga',
      imageObjectKey: objectKey,
      desiredEffects: 'Que sume hambre y dé experiencia al comerlo',
    }), { headers: hCom });
    if (!medir(creada, cicloTrend, 'ciclo · POST /commercial/pet/requests', [201])) return;

    const id = (cuerpo(creada) || {}).id;
    sleep(0.5);

    // 3. El admin la toma: PENDING → IN_REVIEW.
    medir(
      http.patch(`${BASE_URL}/api/admin/pet-requests/${id}/review`, null, { headers: hAdm }),
      cicloTrend, 'ciclo · PATCH /api/admin/pet-requests/{id}/review', [200]
    );
    sleep(0.3);

    // Las impares se rechazan: es el otro estado final y también hay que medirlo.
    if (n % 2 === 1) {
      medir(
        http.patch(`${BASE_URL}/api/admin/pet-requests/${id}/reject`,
          JSON.stringify({ reason: 'Rechazo de la prueba de carga' }), { headers: hAdm }),
        cicloTrend, 'ciclo · PATCH /api/admin/pet-requests/{id}/reject', [200]
      );
      cicloOk.add(1);
      return;
    }

    if (!data.designerUserId) return;

    // 4. Aprobar (aprobar = asignar diseñador) y reasignar al mismo.
    medir(
      http.patch(`${BASE_URL}/api/admin/pet-requests/${id}/approve`, JSON.stringify({
        designerUserId: data.designerUserId,
        adminNotes: 'Aprobada por la prueba de carga',
      }), { headers: hAdm }),
      cicloTrend, 'ciclo · PATCH /api/admin/pet-requests/{id}/approve', [200]
    );
    sleep(0.3);

    medir(
      http.patch(`${BASE_URL}/api/admin/pet-requests/${id}/assign-designer`,
        JSON.stringify({ designerUserId: data.designerUserId }), { headers: hAdm }),
      cicloTrend, 'ciclo · PATCH /api/admin/pet-requests/{id}/assign-designer', [200]
    );
    sleep(0.3);

    // 5. El diseñador arma el ítem. externalId ≥ 1000 y único: por debajo de 15
    //    están los ítems horneados en el build, y la tabla tiene índice único.
    const externalId = 100000 + (Date.now() % 800000) + n;
    medir(
      http.patch(`${BASE_URL}/game-designer/pet/requests/${id}/draft`, JSON.stringify({
        externalId,
        name: `[k6] item ${marca}`,
        description: 'Ítem publicado por la prueba de carga',
        price: 30,
        hungerDelta: 15,
        expWhenEating: 5,
        active: true,
      }), { headers: hDis }),
      cicloTrend, 'ciclo · PATCH /game-designer/pet/requests/{id}/draft', [200]
    );
    sleep(0.3);

    // 6. Publicar: inserta el ítem en el catálogo que consume el juego.
    if (medir(
      http.post(`${BASE_URL}/game-designer/pet/requests/${id}/publish`, null, { headers: hDis }),
      cicloTrend, 'ciclo · POST /game-designer/pet/requests/{id}/publish', [200],
      (b) => b.status === 'COMPLETED'
    )) {
      cicloOk.add(1);
    }
  });
}

// ─── Teardown ─────────────────────────────────────────────────────────────────

export function teardown(data) {
  console.log('');
  console.log('── Rastro dejado en la base ────────────────────────────────');
  console.log(`  · ${data.sesiones.length} sesiones de setup + las de \`sesiones\` (pet_sessions)`);
  console.log('  · escenas con sceneId 9000+ en estado inactivo (DELETE es lógico)');
  console.log('  · comentarios "[k6] ..." en catalog_request_comments');
  if (CON_CICLO) {
    console.log('  · solicitudes "[k6] producto ..." e ítems "[k6] item ..." en el catálogo del juego');
  }
  console.log('  Las sentencias de limpieza están en la cabecera de este archivo.');
}
