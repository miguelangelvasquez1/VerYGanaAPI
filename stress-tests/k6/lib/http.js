// Wrapper HTTP de la prueba de carga.
//  - El nombre de la petición es "METHOD /plantilla/{ruta}" (tag name); la URL con datos nunca se
//    etiqueta ni se imprime (options.systemTags sin url; ver main.js).
//  - Cada paso declara los estados esperados: `ok` (éxito) y `reject` (rechazo de negocio provocado
//    a propósito). Todo lo demás cuenta como error (http_req_failed).
//  - Los cuerpos de respuesta se descartan salvo donde el paso los necesita (json: true).
import http from 'k6/http';
import { BASE_URL } from './config.js';
import { recordCall } from './inventory.js';
import { ensureFresh } from './auth.js';

const callbacks = {};

function expected(statuses) {
  const key = statuses.join(',');
  if (!callbacks[key]) {
    callbacks[key] = http.expectedStatuses(...statuses);
  }
  return callbacks[key];
}

function fill(template, path) {
  return template.replace(/\{([^}]+)\}/g, (_, key) => {
    if (!path || path[key] === undefined || path[key] === null) {
      throw new Error(`falta el parámetro de ruta ${key} en ${template}`);
    }
    return encodeURIComponent(String(path[key]));
  });
}

function queryString(query) {
  if (!query) return '';
  const parts = [];
  Object.keys(query).forEach((k) => {
    const v = query[k];
    if (v !== undefined && v !== null) {
      parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
    }
  });
  return parts.length ? `?${parts.join('&')}` : '';
}

/** Enmascara correos en el texto que se imprime (solo con LOG_FAILURES=1). */
function safeSnippet(text) {
  return String(text || '')
    .replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+/g, '<correo>')
    .replace(/\d{7,}/g, '<num>')
    .slice(0, 140)
    .replace(/\s+/g, ' ');
}

const logged = {};
const LOG_FAILURES = __ENV.LOG_FAILURES === '1';

/**
 * Llama a un endpoint por su nombre ("GET /adLike/next"). Opciones:
 *   session  : { accessToken, ... } de lib/auth.js (omitir en rutas públicas)
 *   path     : valores de {variables} de la ruta
 *   query    : parámetros de consulta
 *   body     : objeto (se envía como JSON) o string (se envía tal cual)
 *   ok       : estados de éxito (por defecto [200])
 *   reject   : estados de rechazo de negocio esperados (por defecto [])
 *   json     : true para parsear el cuerpo (si no, se descarta)
 *   headers  : cabeceras extra
 *   raw      : { body, contentType } para enviar bytes sin pasar por JSON
 * Devuelve { status, ok, rejected, body, res }.
 */
export function call(name, opts = {}) {
  const space = name.indexOf(' ');
  const method = name.slice(0, space);
  const template = name.slice(space + 1);
  const ok = opts.ok || [200];
  const reject = opts.reject || [];

  const headers = Object.assign({}, opts.headers || {});
  if (opts.session) {
    ensureFresh(opts.session);
    headers.Authorization = `Bearer ${opts.session.accessToken}`;
  }
  let payload = null;
  if (opts.raw) {
    payload = opts.raw.body;
    headers['Content-Type'] = opts.raw.contentType;
  } else if (opts.body !== undefined && opts.body !== null) {
    payload = typeof opts.body === 'string' ? opts.body : JSON.stringify(opts.body);
    headers['Content-Type'] = headers['Content-Type'] || 'application/json';
  }

  const url = `${BASE_URL}${fill(template, opts.path)}${queryString(opts.query)}`;
  const params = {
    headers,
    tags: { name },
    responseCallback: expected(ok.concat(reject)),
    timeout: opts.timeout || '30s',
  };
  if (opts.json || LOG_FAILURES) {
    // Con LOG_FAILURES=1 (solo para depurar) también se lee el cuerpo de los fallos para imprimir un extracto.
    params.responseType = 'text';
  }

  const res = http.request(method, url, payload, params);

  const status = res.status;
  const isOk = ok.indexOf(status) !== -1;
  const isRejected = !isOk && reject.indexOf(status) !== -1;
  recordCall(name, res.timings.duration, !(isOk || isRejected), isRejected);

  if (LOG_FAILURES && !isOk) {
    const key = `${name}|${status}|${isRejected}`;
    if (!logged[key]) {
      logged[key] = true;
      let detail = '';
      if (res.body) detail = safeSnippet(res.body);
      console.log(`${isRejected ? 'RECHAZO' : 'ERROR'} ${name} -> ${status} ${detail}`);
    }
  }

  let body = null;
  if (opts.json && res.body) {
    try {
      body = JSON.parse(res.body);
    } catch (e) {
      body = null;
    }
  }
  return { status, ok: isOk, rejected: isRejected, body, res };
}
