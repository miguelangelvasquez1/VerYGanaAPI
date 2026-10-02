// Utilidades compartidas por los recorridos de comercial, diseñador, admin y cumplimiento.
// Los datos que usan son ficticios; nada de aquí imprime correos, teléfonos ni tokens.
import http from 'k6/http';
import encoding from 'k6/encoding';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { call } from './http.js';
import { login, logout } from './auth.js';
import { myIdentifier } from './users.js';
import { SCENARIO, STAGES, thinkTime, totalSeconds } from './config.js';

export const SMOKE = SCENARIO === 'smoke';

// Rechazos de negocio que un recorrido puede provocar sin que sea un error.
export const BUSINESS = [400, 404, 409, 422];

// PNG de 1x1 píxel: Tika lo detecta por sus bytes reales (como en PQRS).
const PNG_BASE64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==';
export const PNG_BYTES = encoding.b64decode(PNG_BASE64, 'std');

export function think() {
  sleep(thinkTime());
}

/** En smoke siempre; en carga con probabilidad `p` (acciones que crean filas y no hace cada sesión). */
export function often(p) {
  return SMOKE || Math.random() < p;
}

export function pick(list) {
  return list && list.length ? list[Math.floor(Math.random() * list.length)] : null;
}

/** Contenido de una respuesta paginada (`data` de PagedResponse o `content` de Page) o de una lista. */
export function rows(body) {
  if (!body) return [];
  if (Array.isArray(body)) return body;
  return body.data || body.content || [];
}

/** Datos únicos por VU y por momento (asuntos, nombres, códigos). */
export function uniqueTag() {
  return `${exec.vu.idInInstance}-${Date.now()}`;
}

/** Claims del JWT de la sesión (publicId, userId). Solo se leen; nunca se imprimen. */
export function claimsOf(session) {
  try {
    return JSON.parse(encoding.b64decode(session.accessToken.split('.')[1], 'rawurl', 's'));
  } catch (e) {
    return {};
  }
}

/** Fecha ISO (yyyy-mm-dd) desplazada `days` días desde hoy. */
export function isoDate(days = 0) {
  return new Date(Date.now() + days * 86400000).toISOString().slice(0, 10);
}

/** Fecha y hora ISO con zona (yyyy-mm-ddTHH:mm:ssZ) desplazada `days` días desde hoy, para ZonedDateTime. */
export function isoDateTime(days = 0) {
  return `${new Date(Date.now() + days * 86400000).toISOString().slice(0, 19)}Z`;
}

/**
 * PUT directo al almacenamiento simulado (MinIO) con una URL prefirmada. No es un endpoint de la API:
 * la etiqueta de nombre es fija para que la URL (que lleva la firma) nunca se registre.
 */
export function putToStorage(uploadUrl, bytes, contentType) {
  return http.put(uploadUrl, bytes, {
    headers: { 'Content-Type': contentType },
    tags: { name: 'PUT {presigned-upload}' },
    timeout: '30s',
  });
}

/**
 * Permiso de subida de una respuesta de prepare-upload. Los DTO de la API difieren: `permission`,
 * `imagePermission` o `uploadUrl` en la raíz; el id sale como `assetId`, `resourceId` o `id`.
 */
export function uploadPermission(body) {
  if (!body) return null;
  const nested = body.permission || body.imagePermission || null;
  const url = (nested && nested.uploadUrl) || body.uploadUrl || null;
  const id = body.assetId || body.resourceId || body.id || null;
  return url ? { id, url, objectKey: (nested && nested.objectKey) || body.objectKey || null } : null;
}

/**
 * Bucle del personal interno: su escenario tiene 1 iteración por VU (un VU por usuario), así
 * que el VU inicia sesión una vez y repite `pass` hasta el final de la prueba.
 * En smoke hace una sola pasada. `pass(session)` recibe la sesión y devuelve cuando termina la ronda.
 */
export function staffLoop(role, pass) {
  const identifier = myIdentifier(role);
  if (SMOKE) {
    const session = login(identifier);
    if (session) {
      pass(session);
      logout(session);
    }
    return;
  }
  const deadline = exec.scenario.startTime + totalSeconds(STAGES[SCENARIO]) * 1000;
  let session = null;
  while (Date.now() < deadline) {
    if (!session) {
      session = login(identifier);
      if (!session) {
        sleep(10);
        continue;
      }
    }
    pass(session);
    sleep(thinkTime());
  }
  if (session) {
    logout(session);
  }
}

export { call };
