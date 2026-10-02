// Sesión de un VU: login una vez, refresh antes de que venza el JWT (15 min) y logout.
// Se usa X-Client-Type: mobile para recibir los tokens en el cuerpo (la web los pone en cookies).
// Los payloads de login y de registro llevan siempre un recaptchaToken de relleno no vacío.
import http from 'k6/http';
import encoding from 'k6/encoding';
import { BASE_URL, PASSWORD, RECAPTCHA_PLACEHOLDER } from './config.js';
import { recordCall } from './inventory.js';

const MOBILE = { 'X-Client-Type': 'mobile', 'Content-Type': 'application/json' };

export function loginPayload(identifier) {
  return { identifier, password: PASSWORD, recaptchaToken: RECAPTCHA_PLACEHOLDER };
}

/** Cuerpo de /auth/register/consumer con datos ficticios; `unique` debe ser distinto por registro. */
export function registerConsumerPayload(unique) {
  const digits = String(unique).replace(/\D/g, '').padStart(7, '0').slice(-7);
  return {
    name: 'Usuario',
    lastName: `Registro ${unique}`,
    email: `lt-reg-${unique}@loadtest.invalid`,
    phoneNumber: `398${digits}`,
    password: PASSWORD,
    municipalityCode: '05001',
    categories: [{ id: 1, name: 'cat' }],
    userName: `ltreg${unique}`.slice(0, 20),
    avatarId: 1,
    birthDate: '1990-05-20',
    gender: 'OTHER',
    ageDeclaration: true,
    termsAccepted: true,
    termsVersion: '1.0',
    documentType: 'CC',
    documentNumber: `8${digits}${digits.slice(0, 2)}`,
    isPEP: false,
    recaptchaToken: RECAPTCHA_PLACEHOLDER,
  };
}

export function registerCommercialPayload(unique) {
  const digits = String(unique).replace(/\D/g, '').padStart(7, '0').slice(-7);
  return {
    email: `lt-reg-c-${unique}@loadtest.invalid`,
    password: PASSWORD,
    phoneNumber: `397${digits}`,
    recaptchaToken: RECAPTCHA_PLACEHOLDER,
  };
}

function expiryOf(token) {
  try {
    const payload = JSON.parse(encoding.b64decode(token.split('.')[1], 'rawurl', 's'));
    return payload.exp * 1000;
  } catch (e) {
    return Date.now() + 10 * 60 * 1000;
  }
}

function record(name, res, okStatus) {
  recordCall(name, res.timings.duration, res.status !== okStatus, false);
}

/** POST /auth/login. Devuelve la sesión o null si el login falló. */
export function login(identifier) {
  const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify(loginPayload(identifier)), {
    headers: MOBILE,
    tags: { name: 'POST /auth/login' },
    responseType: 'text',
    timeout: '30s',
  });
  record('POST /auth/login', res, 200);
  if (res.status !== 200) {
    return null;
  }
  const body = JSON.parse(res.body);
  return {
    accessToken: body.accessToken,
    refreshToken: body.refreshToken,
    expiresAt: expiryOf(body.accessToken),
    scope: body.scope || '',
  };
}

/** POST /auth/refresh. Renueva el par de tokens en la misma sesión; devuelve true si lo logró. */
export function refresh(session) {
  const res = http.post(`${BASE_URL}/auth/refresh`, JSON.stringify({ refreshToken: session.refreshToken }), {
    headers: MOBILE,
    tags: { name: 'POST /auth/refresh' },
    responseType: 'text',
    timeout: '30s',
  });
  record('POST /auth/refresh', res, 200);
  if (res.status !== 200) {
    return false;
  }
  const body = JSON.parse(res.body);
  session.accessToken = body.accessToken;
  session.refreshToken = body.refreshToken || session.refreshToken;
  session.expiresAt = expiryOf(body.accessToken);
  return true;
}

/** Renueva el token si le queda menos de un minuto (el JWT dura 15 min). */
export function ensureFresh(session) {
  if (session.expiresAt - Date.now() < 60 * 1000) {
    refresh(session);
  }
}

/** POST /auth/logout: revoca el refresh token. */
export function logout(session) {
  const res = http.post(`${BASE_URL}/auth/logout`, JSON.stringify({ refreshToken: session.refreshToken }), {
    headers: MOBILE,
    tags: { name: 'POST /auth/logout' },
    timeout: '30s',
  });
  record('POST /auth/logout', res, 204);
}
