// Recorrido del administrador A: SOLO lectura (GET). Nada destructivo:
// bloquear, borrar, aprobar, rechazar, sortear y demás mutaciones de admin están excluidas del inventario
// (motivo `admin`). Los 3 admins son personal interno fijo: cada VU inicia sesión una vez y repite la
// ronda hasta el final de la prueba (lib/util.js staffLoop). En smoke hace una sola ronda.
//
// Los ids de detalle se toman de las listas (publicId de usuarios, ids de rifas, encuestas, solicitudes...);
// cuando la lista sale vacía el detalle se omite, salvo en smoke, donde se consulta un id inexistente para
// ejercitar el endpoint por su camino de rechazo (404/400). Los publicId y correos nunca se imprimen.
import { sleep } from 'k6';
import { call } from '../lib/http.js';
import {
  BUSINESS, SMOKE, isoDate, isoDateTime, pick, rows, staffLoop, think,
} from '../lib/util.js';

const NIL_UUID = '00000000-0000-0000-0000-000000000000';
const PAGE = { page: 0, size: 20 };

function users(session) {
  ['CONSUMER', 'COMMERCIAL', 'GAME_DESIGNER', 'ADMIN', 'COMPLIANCE_OFFICER'].forEach((role) => {
    if (SMOKE || role === 'CONSUMER' || role === 'COMMERCIAL') {
      call('GET /admin/users/stats', { session, query: { role } });
    }
  });
  call('GET /admin/users/news', { session, query: { startDate: isoDate(-30), endDate: isoDate(0), size: 20 } });

  const consumers = call('GET /admin/users/consumers', {
    session, query: Object.assign({ accountStatus: 'ACTIVE' }, PAGE), json: true,
  });
  const consumer = pick(rows(consumers.body));
  if (consumer && consumer.publicId) {
    call('GET /admin/users/consumers/{publicId}', { session, path: { publicId: consumer.publicId } });
    call('GET /users/public-id/{publicId}', { session, path: { publicId: consumer.publicId } });
  }
  think();

  const commercials = call('GET /admin/users/commercials', { session, query: PAGE, json: true });
  const commercial = pick(rows(commercials.body));
  if (commercial && commercial.publicId) {
    call('GET /admin/users/commercials/{publicId}', { session, path: { publicId: commercial.publicId } });
    // Cuenta de prosperidad: solo los comerciales STANDARD la tienen, el resto responde con rechazo.
    call('GET /admin/prosperity/commercials/{publicId}', {
      session, path: { publicId: commercial.publicId }, reject: BUSINESS,
    });
    call('GET /admin/prosperity/commercials/{publicId}/movements', {
      session, path: { publicId: commercial.publicId }, query: PAGE, reject: BUSINESS,
    });
  }
  call('GET /admin/users/commercials', { session, query: Object.assign({ currentPlan: 'PREMIUM' }, PAGE) });

  [
    ['GET /admin/users/admins', 'GET /admin/users/admins/{publicId}'],
    ['GET /admin/users/game-designers', 'GET /admin/users/game-designers/{publicId}'],
    ['GET /admin/users/compliance-officers', 'GET /admin/users/compliance-officers/{publicId}'],
  ].forEach(([listName, detailName]) => {
    const list = call(listName, { session, query: PAGE, json: true });
    const item = pick(rows(list.body));
    if (item && item.publicId) {
      call(detailName, { session, path: { publicId: item.publicId } });
    }
  });
  call('GET /users/me', { session });
  // El correo sembrado (dominio .invalid) viaja en la ruta; la URL nunca se etiqueta.
  call('GET /users/email/{email}', { session, path: { email: 'lt-consumer-1@loadtest.invalid' }, reject: BUSINESS });
}

function auditAndSecurity(session) {
  const range = { from: isoDateTime(-7), to: isoDateTime(1) };
  call('GET /admin/audit-logs', { session, query: Object.assign({ level: 'INFO', size: 50 }, range) });
  call('GET /admin/audit-logs/critical', { session, query: Object.assign({ size: 50 }, range) });
  call('GET /admin/security-events', { session, query: Object.assign({ size: 50 }, range) });
  call('GET /admin/security-events/critical', { session, query: Object.assign({ size: 50 }, range) });
}

function marketplace(session) {
  call('GET /admin/products', { session, query: { status: 'ACTIVE', page: 0, size: 5 } });
  call('GET /admin/products/productCategories/inactives', { session });
  call('GET /ads/admin/all', { session, query: { status: 'ACTIVE', page: 0, size: 20 } });
  const surveys = call('GET /surveys/admin', { session, query: { page: 0, size: 10 }, json: true });
  const survey = pick(rows(surveys.body));
  if (survey) {
    call('GET /surveys/admin/{surveyId}', { session, path: { surveyId: survey.id } });
  }
  call('GET /impact-stories', { session, query: { status: 'PUBLISHED', page: 0, size: 10 } });
  call('GET /admin/pricing-configs', { session });
  call('GET /system-features', { session });
  call('GET /admin/legal-documents/{type}/history', { session, path: { type: 'USERS_TERMS_AND_CONDITIONS' }, reject: BUSINESS });
  const refunds = call('GET /admin/cash-refunds', { session, query: { page: 0, size: 20 }, json: true });
  const refund = pick(rows(refunds.body));
  // Sin reembolsos sembrados el detalle por ítem sale por el camino de rechazo (404).
  call('GET /admin/cash-refunds/by-purchase-item/{purchaseItemId}', {
    session, path: { purchaseItemId: refund && refund.purchaseItemId ? refund.purchaseItemId : 1 }, reject: BUSINESS,
  });
}

function finance(session) {
  call('GET /admin/treasury/balance', { session });
  call('GET /admin/treasury/config/keys-reserve-pct', { session });
  ['KEYS_RESERVE', 'OPERATIONS', 'PAYOUTS_PENDING'].forEach((code) => {
    if (SMOKE || code === 'KEYS_RESERVE') {
      call('GET /admin/treasury/movements/{code}', { session, path: { code }, query: { page: 0, size: 20 } });
    }
  });
  const payouts = call('GET /admin/payouts', { session, query: { date: isoDate(-1) }, json: true });
  const payout = pick(rows(payouts.body));
  // wompi-status consulta el estado al simulador de payouts; sin payouts sembrados responde 404.
  call('GET /admin/payouts/{id}/wompi-status', {
    session, path: { id: payout && payout.id ? payout.id : NIL_UUID }, reject: BUSINESS,
  });
  const methods = call('GET /admin/payout-methods', {
    session, query: Object.assign({ status: 'UNDER_REVIEW' }, PAGE), json: true,
  });
  const method = pick(rows(methods.body));
  call('GET /admin/payout-methods/{id}/certificate', {
    session, path: { id: method && method.id ? method.id : 1 }, reject: BUSINESS,
  });
}

function raffles(session) {
  call('GET /api/raffles', { session, query: { status: 'ACTIVE', page: 0, size: 10 } });
  // Las estadísticas y la verificación del sorteo solo existen para rifas COMPLETED.
  const list = call('GET /api/raffles', { session, query: { status: 'COMPLETED', page: 0, size: 10 }, json: true });
  const raffle = pick(rows(list.body));
  const raffleId = raffle ? raffle.id : 1;
  if (raffle || SMOKE) {
    call('GET /api/admin/raffles/{raffleId}/stats', { session, path: { raffleId }, reject: BUSINESS });
    call('GET /api/admin/raffles/{raffleId}/verify', { session, path: { raffleId }, reject: BUSINESS });
  }
  call('GET /api/admin/raffles/count', { session, query: { status: 'ACTIVE' } });
  call('GET /api/admin/raffles/audit-logs', { session, query: { from: isoDate(-7), to: isoDate(0), page: 0, size: 20 } });
  call('GET /api/admin/raffles/audit-logs/suspicious', { session, query: { since: isoDate(-7), threshold: 5 } });
  call('GET /api/admin/raffles/tickets/{ticketId}/audit-logs', { session, path: { ticketId: 1 }, reject: BUSINESS });
  call('GET /api/admin/ticket-rules', { session, query: { isActive: true, page: 0, size: 10 } });
  call('GET /api/admin/ticket-rules/count/active', { session });
}

function designRequests(session) {
  const branding = call('GET /api/admin/branding-requests', { session, json: true });
  const brandingRequest = pick(rows(branding.body));
  call('GET /api/admin/branding-requests/designers', { session });
  if (brandingRequest) {
    call('GET /api/admin/branding-requests/{id}', { session, path: { id: brandingRequest.id } });
  }
  const pets = call('GET /api/admin/pet-requests', { session, json: true });
  const petRequest = pick(rows(pets.body));
  call('GET /api/admin/pet-requests/designers', { session });
  if (petRequest) {
    call('GET /api/admin/pet-requests/{id}', { session, path: { id: petRequest.id } });
    call('GET /api/admin/pet-requests/{id}/comments', { session, path: { id: petRequest.id } });
  } else if (SMOKE) {
    call('GET /api/admin/pet-requests/{id}', { session, path: { id: 1 }, reject: BUSINESS });
    call('GET /api/admin/pet-requests/{id}/comments', { session, path: { id: 1 }, reject: BUSINESS });
  }
  const pqrs = call('GET /admin/pqrs', { session, query: { page: 0, size: 20 }, json: true });
  const ticket = pick(rows(pqrs.body));
  if (ticket) {
    call('GET /admin/pqrs/{id}', { session, path: { id: ticket.id } });
  } else if (SMOKE) {
    call('GET /admin/pqrs/{id}', { session, path: { id: 1 }, reject: BUSINESS });
  }
}

// Una ronda del administrador: las secciones se alternan para repartir la carga de lectura.
function round(session) {
  const sections = [users, auditAndSecurity, marketplace, finance, raffles, designRequests];
  if (SMOKE) {
    sections.forEach((section) => section(session));
    return;
  }
  const section = pick(sections);
  section(session);
  sleep(1);
}

export function adminLoop() {
  staffLoop('admin', round);
}

export function adminSmoke() {
  staffLoop('admin', round);
}
