// Recorridos del consumidor C0 a C8.
//
// Cada iteración de un VU es una sesión: inicia sesión, hace C0 (siempre), un recorrido elegido con
// pesos (C1 35 %, C2 20 %, C3 10 %, C4 10 %, C5 10 %, C6 8 %, C7 5 %) y cierra sesión. C8 (2 %) es el
// registro de un usuario nuevo y no lleva sesión. En smoke una sola iteración recorre todos.
//
// Reglas que se respetan aquí:
//  - Cada llamada usa el nombre 'METHOD /plantilla/{ruta}' (lib/http.js): la URL con datos nunca se
//    etiqueta ni se imprime. Los correos y teléfonos de los datos ficticios no salen en
//    ninguna salida.
//  - Un estado de negocio provocado a propósito (cooldown, camino de rechazo por falta de buzón, cupos) se declara
//    en `reject` y se reporta aparte, no como error.
//  - Los pagos, firmas y archivos van a WireMock y MinIO; el webhook de Wompi que el flujo de compra
//    necesita de vuelta lo firma lib/wompi.js.
import http from 'k6/http';
import encoding from 'k6/encoding';
import { sleep } from 'k6';
import exec from 'k6/execution';
import { login, logout, refresh, registerConsumerPayload } from '../lib/auth.js';
import { call } from '../lib/http.js';
import { myIdentifier } from '../lib/users.js';
import { buildEvent } from '../lib/wompi.js';
import {
  OTP_CODE, PASSWORD, SCENARIO, WOMPI_EVENTS_KEY, sessionGap, thinkTime,
} from '../lib/config.js';

const SMOKE = SCENARIO === 'smoke';

// Encuestas sembradas a las que se puede entrar por id cuando la lista del consumidor sale vacía.
const SURVEY_IDS = Number(__ENV.SURVEY_IDS || 100);

// PNG de 1x1 píxel para la evidencia de PQRS: Tika lo detecta por sus bytes reales.
const PNG_BASE64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==';
const PNG_BYTES = encoding.b64decode(PNG_BASE64, 'std');

// Cuerpo opaco del guardado de mascota (unos 2 KB, como el del juego).
const PET_SAVE_BLOB = JSON.stringify({ v: 1, pad: 'x'.repeat(2000) });

// Rechazos de negocio que cualquier recorrido puede provocar (se reportan aparte).
const BUSINESS = [400, 404, 409, 422];

function think() {
  sleep(thinkTime());
}

/** En smoke siempre; en carga con probabilidad `p` (acciones que crean filas y que no hace cada sesión). */
function often(p) {
  return SMOKE || Math.random() < p;
}

function pick(list) {
  return list && list.length ? list[Math.floor(Math.random() * list.length)] : null;
}

function queryParam(url, key) {
  const m = new RegExp(`[?&]${key}=([^&]*)`).exec(url || '');
  return m ? decodeURIComponent(m[1]) : null;
}

/** Datos únicos por VU y por momento (registro, teléfono nuevo, asuntos). */
function uniqueTag() {
  return `${exec.vu.idInInstance}${Date.now()}`;
}

/** Contenido de una respuesta paginada de PagedResponse (`data`) o de Page (`content`). */
function rows(body) {
  if (!body) return [];
  return body.data || body.content || (Array.isArray(body) ? body : []);
}

// ---------------------------------------------------------------------------------------------
// C0 · sesión de consumidor (siempre)
// ---------------------------------------------------------------------------------------------
function c0(session) {
  call('GET /consumers/initialData', { session });
  call('GET /consumers/profile', { session });
  call('GET /consumers/available-keys', { session });
  call('GET /consumer/wallet/keys/balance', { session });
  call('GET /api/levels/me', { session });
  think();
  call('GET /notifications', { session });
  call('GET /notifications/unread/count', { session });
  call('PATCH /notifications/read-all', { session, ok: [204] });
  // Renueva el par de tokens a mitad de sesión, como hace el cliente cuando el JWT está por vencer.
  refresh(session);
}

// ---------------------------------------------------------------------------------------------
// C1 · anuncios
// ---------------------------------------------------------------------------------------------
function c1(session) {
  const next = call('GET /adLike/next', { session, json: true, ok: [200, 204], reject: BUSINESS });
  const ad = next.body;
  if (ad && ad.id) {
    think();
    call('POST /commercials/page-visits', {
      session, body: { adId: ad.id, targetUrl: ad.targetUrl || undefined }, ok: [200, 201, 204], reject: BUSINESS,
    });
    // El servidor exige haber visto el 95 % de la duración del anuncio antes de completar o dar like.
    sleep(Math.min(ad.durationSeconds || 15, 60) * 0.97 + 0.5);
    const watch = { sessionUUID: ad.sessionUUID, adId: ad.id };
    call('POST /adLike/complete', { session, body: watch, ok: [204], reject: BUSINESS });
    call('POST /adLike/like', { session, body: watch, ok: [200], reject: BUSINESS });
  }
  think();
  call('GET /key-transactions', { session });
  call('GET /key-transactions/total-earned-keys', { session });
  call('GET /key-transactions/total-used-keys', { session });
  call('GET /key-transactions/total-expired-keys', { session });
}

// ---------------------------------------------------------------------------------------------
// C2 · juegos
// ---------------------------------------------------------------------------------------------
function c2(session) {
  const games = call('GET /games', { session, json: true });
  const game = pick(rows(games.body));
  if (game) {
    call('GET /games/{id}/schema', { session, path: { id: game.id }, reject: BUSINESS });
  }
  think();
  const init = call('POST /games/init', {
    session, body: { sponsored: true }, json: true, reject: BUSINESS,
  });
  const url = init.body && init.body.url;
  const sessionToken = queryParam(url, 'session_token');
  const userHash = queryParam(url, 'user_hash');
  if (sessionToken && userHash) {
    const event = {
      sessionToken, userHash, isBrandedMode: true, campaignId: Number(queryParam(url, 'campaign_id')),
    };
    // El juego corre en un iframe sin JWT: la credencial es session_token + user_hash.
    call('POST /games/assets', { body: event, reject: BUSINESS });
    sleep(thinkTime());
    call('POST /games/metrics', {
      session, body: Object.assign({ payload: [{ key: 'score', type: 'INT', value: 120 }] }, event), reject: BUSINESS,
    });
    call('POST /games/end-session', {
      body: Object.assign({
        payload: { devicePlatform: 'MOBILE', finalScore: 120, finalMetrics: [{ key: 'score', type: 'INT', value: 120 }] },
      }, event),
      reject: BUSINESS,
    });
  }
  think();
  call('GET /api/levels/me/history', { session });
  call('GET /api/levels/config', { session });
}

// ---------------------------------------------------------------------------------------------
// C3 · rifas, premios y llaves
// ---------------------------------------------------------------------------------------------
function c3(session) {
  call('GET /api/raffles/lives', { session });
  const actives = call('GET /api/raffles/actives', { session, json: true });
  const raffle = pick(rows(actives.body));
  if (raffle) {
    call('GET /api/raffles/{raffleId}', { session, path: { raffleId: raffle.id } });
    call('GET /api/raffles/{raffleId}/draw-status', { session, path: { raffleId: raffle.id } });
    call('GET /api/my/raffle-tickets/raffle/{raffleId}', { session, path: { raffleId: raffle.id } });
  }
  think();
  call('GET /api/raffles/me', { session, query: { status: 'ACTIVE' } });
  call('GET /api/raffles/me/count', { session, query: { status: 'ACTIVE' } });
  call('GET /api/my/raffle-tickets/winners', { session });
  call('GET /api/my/raffle-tickets/winners/balance', { session });
  think();

  const last = call('GET /results/last', { session, json: true });
  const finished = pick(last.body);
  if (finished) {
    call('GET /results/raffle/{raffleId}', { session, path: { raffleId: finished.raffleId } });
    call('GET /results/raffle/{raffleId}/draw-proof', { session, path: { raffleId: finished.raffleId } });
  }
  call('GET /winners/last', { session });

  // Reclamo del premio: lo tiene el 1 % de los consumidores sembrados (OTP fijo 000000).
  const prizes = call('GET /winners/my-prizes', { session, json: true });
  const pending = rows(prizes.body).find((p) => !p.claimed && !p.isClaimed);
  const phone = `396${String(Date.now()).slice(-7)}`;
  call('POST /winners/claim/send-otp', { session, query: { phoneNumber: phone }, ok: [200, 202] });
  call('POST /winners/claim/send-email-otp', {
    session, query: { email: `lt-claim-${uniqueTag()}@loadtest.invalid` }, ok: [200, 202],
  });
  if (pending) {
    call('POST /winners/claim', {
      session,
      body: { prizeId: pending.prizeId, deliveryMethod: 'SMS', newPhoneNumber: phone, smsOtpCode: OTP_CODE },
      ok: [200, 204],
      reject: BUSINESS,
    });
  } else if (SMOKE) {
    // Sin premio pendiente en smoke: un reclamo sobre un premio inexistente ejercita el endpoint.
    call('POST /winners/claim', {
      session, body: { prizeId: -1, deliveryMethod: 'EMAIL' }, ok: [200, 204], reject: BUSINESS,
    });
  }
  // Gasto de llaves de compra (el endpoint que usa el juego de mascota).
  call('POST /consumer/wallet/keys/spend', {
    session, body: { amount: 1, itemId: 0, itemName: 'monoculo' }, reject: BUSINESS,
  });
}

// ---------------------------------------------------------------------------------------------
// C4 · mascotas (la capa de juego es pública, con session_token + user_hash)
// ---------------------------------------------------------------------------------------------
function c4(session) {
  const init = call('POST /pet/session/init', { session, json: true });
  const url = init.body && init.body.url;
  const cred = { session_token: queryParam(url, 'session_token'), user_hash: queryParam(url, 'user_hash') };
  if (!cred.session_token || !cred.user_hash) {
    return;
  }
  call('POST /pet/catalog', { body: cred });
  call('POST /pet/scenes', { body: cred });
  call('POST /pet/scenes-objects', { body: cred });
  think();
  const notifications = call('POST /pet/notifications', { body: cred, json: true });
  call('GET /pet/save', { session, ok: [200, 204] });
  call('PUT /pet/save', { session, body: { data: PET_SAVE_BLOB } });
  const notification = pick(notifications.body && notifications.body.notifications);
  if (notification) {
    call('PATCH /pet/notifications/{id}/read', { body: cred, path: { id: notification.id } });
  }
}

// ---------------------------------------------------------------------------------------------
// C5 · marketplace y compra (pago simulado con webhook firmado)
// ---------------------------------------------------------------------------------------------
function c5(session) {
  call('GET /productCategories', { session });
  const filtered = call('GET /products/filter', { session, query: { page: 0 }, json: true });
  const products = rows(filtered.body);
  const product = pick(products);
  if (product) {
    call('GET /products/{productId}', { session, path: { productId: product.id } });
    call('GET /productsReviews/{productId}', { session, path: { productId: product.id } });
    if (product.commercialPublicId) {
      call('GET /products/commercial/{publicId}', { session, path: { publicId: product.commercialPublicId } });
    }
    think();
    call('POST /products/favorites/{productId}', { session, path: { productId: product.id }, ok: [200, 201, 204], reject: BUSINESS });
    call('GET /products/favorites', { session });
    call('GET /products/favorites/count', { session });
    call('DELETE /products/favorites/{productId}', { session, path: { productId: product.id }, ok: [200, 204], reject: BUSINESS });
  }
  think();

  const purchases = call('GET /purchases', { session, json: true });
  const history = rows(purchases.body);

  // Compra con dinero real simulado: Wompi (WireMock) crea el checkout y el webhook firmado la aprueba.
  const buyable = products.filter((p) => p.status === 'ACTIVE' && p.stock > 0);
  const target = pick(buyable);
  if (target) {
    const buy = call('POST /purchases/buy', {
      session,
      body: { items: [{ productId: target.id, quantity: 1 }], keysToUse: 0 },
      ok: [200, 201],
      reject: BUSINESS,
      json: true,
    });
    const order = buy.body;
    if (order && order.purchaseId) {
      sleep(thinkTime());
      const event = buildEvent({
        id: `lt-tx-${order.purchaseId}-${exec.vu.idInInstance}`,
        reference: order.referenceId,
        status: 'APPROVED',
        amountInCents: order.cashAmountCents,
        eventsKey: WOMPI_EVENTS_KEY,
      });
      call('POST /wompi/events', {
        raw: { body: event.body, contentType: 'application/json' },
        headers: { 'x-event-checksum': event.checksum },
      });
      // El webhook se procesa de forma asíncrona: se da un momento antes de leer el estado.
      sleep(SMOKE ? 2 : 1);
      call('GET /purchases/{purchaseId}', { session, path: { purchaseId: order.purchaseId }, json: true });
    }
  }

  // Posventa sobre lo comprado en el historial sembrado.
  const items = history.length ? history[0].items || [] : [];
  const item = pick(items);
  if (item) {
    think();
    const delivered = items.find((i) => i.deliveredAt);
    if (delivered) {
      call('GET /purchaseItems/{purchaseItemId}/delivered-code', {
        session, path: { purchaseItemId: delivered.id }, reject: BUSINESS,
      });
    }
    const reviewable = items.find((i) => i.canBeReviewed);
    if (reviewable && often(0.3)) {
      call('POST /productsReviews/create', {
        session, body: { purchaseItemId: reviewable.id, comment: 'Reseña de la prueba de carga', rating: 4 },
        ok: [200, 201], reject: BUSINESS,
      });
    }
    if (often(0.05)) {
      call('POST /purchaseItems/{purchaseItemId}/report', {
        session, path: { purchaseItemId: item.id },
        body: { reason: 'OTHER', description: 'Reporte de la prueba de carga' },
        ok: [200, 201], reject: BUSINESS,
      });
      call('POST /purchaseItems/{purchaseItemId}/cash-refund/bank-details', {
        session, path: { purchaseItemId: item.id },
        body: {
          accountHolderName: 'Titular Ficticio', accountHolderDoc: '1000000000', accountHolderDocType: 'CC',
          bankName: 'Banco Ficticio', accountNumber: '000000000000', accountType: 'SAVINGS',
        },
        ok: [200, 201, 204], reject: BUSINESS,
      });
    }
  }
}

// ---------------------------------------------------------------------------------------------
// C6 · encuestas
// ---------------------------------------------------------------------------------------------
function answerFor(question) {
  const options = question.options || [];
  switch (question.type) {
    case 'SINGLE_CHOICE':
      return { questionId: question.id, selectedOptionId: options.length ? options[0].id : null };
    case 'MULTIPLE_CHOICE':
      return { questionId: question.id, selectedOptionIds: options.slice(0, 2).map((o) => o.id) };
    case 'RATING':
      return { questionId: question.id, textAnswer: '4' };
    case 'YES_NO':
      return { questionId: question.id, textAnswer: 'YES' };
    default:
      return { questionId: question.id, textAnswer: 'Respuesta de la prueba de carga' };
  }
}

function c6(session) {
  const available = call('GET /surveys', { session, json: true });
  call('GET /surveys/rewards/summary', { session });
  // Hoy la lista sale vacía para todo consumidor con género: la consulta compara el género del
  // consumidor con el de la audiencia, y las encuestas sembradas son 'ALL' (hallazgo conocido: el filtro de género no contempla ALL).
  // Con la lista vacía se abre una encuesta por id, como si el consumidor llegara por un enlace, para
  // que el inicio y el envío (las escrituras) se sigan midiendo; una ya contestada cuenta como rechazo.
  const listed = pick(rows(available.body));
  const surveyId = listed ? listed.id : 1 + Math.floor(Math.random() * SURVEY_IDS);
  think();
  call('GET /surveys/{surveyId}', { session, path: { surveyId }, reject: BUSINESS });
  const started = call('POST /surveys/{surveyId}/start', {
    session, path: { surveyId }, json: true, reject: BUSINESS,
  });
  const run = started.body;
  if (run && run.sessionId && run.survey) {
    sleep(thinkTime());
    call('POST /surveys/submit', {
      session,
      body: { sessionId: run.sessionId, answers: (run.survey.questions || []).map(answerFor) },
      reject: BUSINESS,
    });
  }
}

// ---------------------------------------------------------------------------------------------
// C7 · perfil, referidos, PQRS, documentos legales, ubicaciones y categorías
// ---------------------------------------------------------------------------------------------
function pqrsWithEvidence(session) {
  const prepared = call('POST /pqrs/assets/prepare-upload', {
    session,
    body: { originalFileName: 'evidencia.png', contentType: 'image/png', sizeBytes: PNG_BYTES.byteLength },
    json: true,
    reject: BUSINESS,
  });
  const upload = prepared.body;
  let assetId = null;
  if (upload && upload.assetId && upload.permission) {
    // PUT directo al almacenamiento simulado (MinIO) con la URL prefirmada; no es un endpoint de la API.
    const put = http.put(upload.permission.uploadUrl, PNG_BYTES, {
      headers: { 'Content-Type': 'image/png' },
      tags: { name: 'PUT {presigned-upload}' },
      timeout: '30s',
    });
    if (put.status === 200) {
      const confirmed = call('POST /pqrs/assets/{id}/confirm', { session, path: { id: upload.assetId }, reject: BUSINESS });
      if (confirmed.ok) {
        assetId = upload.assetId;
        call('GET /pqrs/assets/{id}/view', { session, path: { id: assetId }, reject: BUSINESS });
      }
    }
  }
  const created = call('POST /pqrs', {
    session,
    body: {
      type: 'PETICION',
      subject: 'Consulta de la prueba de carga',
      description: 'Solicitud ficticia generada por la prueba de carga.',
      assetIds: assetId ? [assetId] : [],
    },
    ok: [200, 201],
    json: true,
    reject: BUSINESS,
  });
  call('GET /pqrs/mine', { session });
  if (created.body && created.body.id) {
    call('GET /pqrs/{id}', { session, path: { id: created.body.id } });
  }
}

function c7(session) {
  call('GET /referrals/my-code', { session });
  call('GET /referrals/my-referrals', { session });
  think();

  const avatars = call('GET /avatars', { session, json: true });
  const avatar = pick(avatars.body);
  if (avatar) {
    call('PATCH /avatars/me', { session, body: { avatarId: avatar.id }, ok: [200, 204], reject: BUSINESS });
  }
  // Se reenvía el departamento y el municipio actuales: el perfil queda igual y la escritura es real.
  const profile = call('GET /consumers/profile', { session, json: true });
  if (profile.body) {
    call('PUT /consumers/profile/edit', {
      session,
      body: { department: profile.body.department, municipalityName: profile.body.municipalityName },
      ok: [200, 204],
      reject: BUSINESS,
    });
  }
  think();

  if (often(0.2)) {
    // Cambio de teléfono por OTP fijo (el falso de SMS acepta solo 000000): número nuevo ficticio.
    const phone = `395${String(Date.now()).slice(-7)}`;
    call('POST /users/me/phone/request-change', {
      session, body: { newPhoneNumber: phone }, ok: [200, 202], reject: BUSINESS,
    });
    call('POST /users/me/phone/verify-change', {
      session, body: { newPhoneNumber: phone, otpCode: OTP_CODE }, reject: BUSINESS,
    });
  }
  think();

  if (often(0.3)) {
    pqrsWithEvidence(session);
  }
  think();

  call('GET /legal-documents', { session });
  call('GET /legal-documents/{type}', { session, path: { type: 'PRIVACY_POLICY' }, reject: BUSINESS });
  const departments = call('GET /locations/departments', { session, json: true });
  const department = pick(departments.body);
  if (department) {
    call('GET /locations/departments/{code}', { session, path: { code: department.code } });
    const municipalities = call('GET /locations/departments/{departmentCode}/municipalities', {
      session, path: { departmentCode: department.code }, json: true,
    });
    const municipality = pick(municipalities.body);
    if (municipality) {
      call('GET /locations/municipalities/{code}', { session, path: { code: municipality.code } });
    }
  }
  const categories = call('GET /categories/all', { session, json: true });
  const category = pick(categories.body);
  if (category) {
    call('GET /categories/{id}', { session, path: { id: category.id } });
  }
  const stories = call('GET /impact-stories/consumer', { session, json: true });
  const story = pick(rows(stories.body));
  if (story) {
    call('GET /impact-stories/{id}', { session, path: { id: story.id } });
  } else if (SMOKE) {
    // Sin historias publicadas en smoke: un id inexistente ejercita el endpoint.
    call('GET /impact-stories/{id}', { session, path: { id: 1 }, reject: BUSINESS });
  }
}

// ---------------------------------------------------------------------------------------------
// C8 · registro de un consumidor nuevo (sin sesión)
// ---------------------------------------------------------------------------------------------
function c8() {
  const unique = uniqueTag();
  const payload = registerConsumerPayload(unique);
  const email = payload.email;
  const phone = payload.phoneNumber;

  // Las categorías y el avatar del registro deben existir: se piden como lo hace el formulario.
  const categories = call('GET /categories/all', { json: true });
  const avatars = call('GET /avatars', { json: true });
  const category = pick(categories.body);
  const avatar = pick(avatars.body);
  // El registro exige aceptar la versión vigente de los términos: se lee del documento activo.
  const terms = call('GET /legal-documents/{type}', {
    path: { type: 'USERS_TERMS_AND_CONDITIONS' }, json: true, reject: BUSINESS,
  });
  if (category) payload.categories = [{ id: category.id, name: category.name }];
  if (avatar) payload.avatarId = avatar.id;
  if (terms.body && terms.body.version) payload.termsVersion = terms.body.version;

  // Las rutas de /users/exists llevan correo y teléfono (datos ficticios); la URL nunca se etiqueta.
  call('GET /users/exists/email/{email}', { path: { email } });
  call('GET /users/exists/phoneNumber/{phoneNumber}', { path: { phoneNumber: phone } });
  think();

  const registered = call('POST /auth/register/consumer', { body: payload, ok: [200, 201], reject: BUSINESS });
  if (!registered.ok) {
    return;
  }
  think();

  // Sin buzón de correo, los endpoints que dependen del código se ejercitan por el camino de rechazo.
  call('POST /auth/verify-email', { body: { email, code: '999999' }, reject: BUSINESS });
  call('POST /auth/resend-verification', { body: { email }, reject: BUSINESS });
  call('POST /auth/forgot-password', { body: { email }, reject: BUSINESS });
  call('POST /auth/reset-password', { body: { email, code: '999999', newPassword: PASSWORD }, reject: BUSINESS });
  think();

  // Activación por SMS con el OTP fijo del falso.
  call('POST /auth/send-phone-verification', { body: { email }, reject: BUSINESS });
  call('POST /auth/verify-phone', { body: { email, code: OTP_CODE }, reject: BUSINESS });
}

// ---------------------------------------------------------------------------------------------
// Sesiones
// ---------------------------------------------------------------------------------------------
const WEIGHTED = [
  [35, c1], [20, c2], [10, c3], [10, c4], [10, c5], [8, c6], [5, c7], [2, c8],
];

function chooseJourney() {
  const total = WEIGHTED.reduce((acc, [w]) => acc + w, 0);
  let roll = Math.random() * total;
  for (const [weight, journey] of WEIGHTED) {
    roll -= weight;
    if (roll < 0) return journey;
  }
  return c1;
}

function withLogin(journeys) {
  const session = login(myIdentifier('consumer'));
  if (!session) {
    return;
  }
  c0(session);
  journeys.forEach((journey) => {
    think();
    journey(session);
  });
  logout(session);
}

export function consumerSession() {
  const journey = chooseJourney();
  if (journey === c8) {
    c8();
  } else {
    withLogin([journey]);
  }
  sleep(sessionGap());
}

export function consumerSmoke() {
  withLogin([c1, c2, c3, c4, c5, c6, c7]);
  c8();
}
