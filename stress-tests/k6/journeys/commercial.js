// Recorrido del comercial M. Cada iteración de un VU es una sesión: inicia sesión,
// hace M0 (siempre) y una o más áreas elegidas con pesos, y cierra sesión. En smoke una sola iteración
// recorre todas las áreas.
//
// Asignación de usuario: (vu.idInInstance - 1) % comerciales sembrados (lib/users.js). Es único en toda la
// prueba pero no denso por escenario, así que unos pocos comerciales comparten cuenta (colisión aceptada:
// solo comparten sesión y datos). Los comerciales sembrados ya terminaron el onboarding y tienen contrato
// APPROVED, plan BASIC/STANDARD/PREMIUM (40/40/20 %) y 500.000 COP de presupuesto.
//
// Reglas de este archivo:
//  - Los rechazos de negocio provocados a propósito (plan sin capacidad -> 403, estado que no permite la
//    acción -> 400/409/422, onboarding ya terminado) van en `reject` y se reportan aparte.
//  - Lo que crea lo cancela, borra o deja inocuo en la misma sesión (anuncio sin aprobar, producto,
//    solicitud de brandeo cancelada), para no desfasar el volumen sembrado.
//  - El perfil se reenvía con los mismos valores: ni el correo ni el teléfono cambian (son el login).
//  - Los pagos, firmas y archivos van a WireMock y MinIO. Ningún dato personal sale en las salidas.
import { sleep } from 'k6';
import { login, logout, registerCommercialPayload } from '../lib/auth.js';
import { call } from '../lib/http.js';
import { availableFor, identifierFor, myIdentifier } from '../lib/users.js';
import { SCENARIO, sessionGap, OTP_CODE } from '../lib/config.js';
import {
  PNG_BYTES, SMOKE, claimsOf, isoDate, often, pick, putToStorage, rows, think, uniqueTag, uploadPermission,
} from '../lib/util.js';

// 403 = el plan no incluye la capacidad (RequirePlanCapability): rechazo esperado para BASIC.
const REJ = [400, 403, 404, 409, 422];

// En smoke el estado del plan que no aplica responde 500 (IllegalStateException sin mapear en el handler
// global: hallazgo conocido, no se arregla aquí); solo en smoke se tolera para poder ejercitar el endpoint.
const REJ_ILLEGAL_STATE = SMOKE ? REJ.concat([500]) : REJ;

const YEAR = new Date().getFullYear();
const MONTH = new Date().getMonth() + 1;

/** Respuesta de /surveys/cost-per-question: { costPerQuestion } en pesos; el precio por pregunta va en centavos. */
function costOf(body) {
  return body && typeof body.costPerQuestion === 'number' ? Math.round(body.costPerQuestion * 100) : 2500;
}

/** Número (base 1) del comercial sembrado a partir de su identificador; el correo nunca se imprime. */
function numberOf(identifier) {
  const m = /lt-commercial-(\d+)@/.exec(identifier);
  return m ? m[1] : '1';
}

// ---------------------------------------------------------------------------------------------
// M0 · sesión (siempre)
// ---------------------------------------------------------------------------------------------
function m0(session, identifier) {
  const initial = call('GET /commercials/initialData', { session, json: true });
  const profile = call('GET /commercials/profile', { session, json: true });
  call('GET /commercial/dashboard/summary', { session, query: { period: 'LAST_30_DAYS' } });
  const state = call('GET /plans/commercial/state', { session, json: true });
  const st = state.body || {};
  // Capacidades del plan: cada comercial solo visita las áreas que su plan incluye (BASIC no anuncia ni usa
  // juegos o encuestas; PREMIUM no vende productos propios y es el único con mascotas). El rechazo por plan
  // es un comportamiento real de la API, pero un cliente real no llamaría lo que su plan no incluye.
  const caps = {
    plan: st.effectivePlan || 'BASIC',
    advertise: !!st.canAdvertise,
    games: !!st.canUseGames,
    surveys: !!st.canUseSurveys,
    performance: !!st.canViewPerformanceMetrics,
    pageVisits: !!st.canViewPageVisitMetrics,
    products: st.maxProducts > 0,
    recharge: st.effectivePlan === 'STANDARD' || st.effectivePlan === 'PREMIUM',
    pets: st.effectivePlan === 'PREMIUM',
  };
  return {
    initial: initial.body || {}, profile: profile.body || {}, publicId: claimsOf(session).publicId, caps,
    number: numberOf(identifier),
  };
}

// ---------------------------------------------------------------------------------------------
// Cuenta, reportes y billetera
// ---------------------------------------------------------------------------------------------
function account(session, me) {
  if (me.publicId) {
    call('GET /commercials/{publicId}/profile', { session, path: { publicId: me.publicId } });
  }
  // Se reenvían los datos actuales: el perfil queda igual y la escritura es real.
  const p = me.profile;
  if (p.email && p.phoneNumber && p.legalRepDocType && often(0.1)) {
    call('PUT /commercials/profile/edit', {
      session,
      body: {
        email: p.email, phoneNumber: p.phoneNumber, address: p.address || 'Dirección ficticia 1',
        legalRepFirstName: p.legalRepFirstName || 'Representante', legalRepLastName: p.legalRepLastName || 'Prueba',
        legalRepDocType: p.legalRepDocType, legalRepDocNumber: p.legalRepDocNumber || '1000000000',
        legalRepPepDeclaration: !!p.legalRepPepDeclaration, whatsappAvailable: !!p.whatsappAvailable,
        whatsappNumber: p.whatsappNumber || undefined,
      },
      ok: [200, 204], reject: REJ,
    });
  }
}

function reports(session, me) {
  const range = { from: isoDate(-30), to: isoDate(0) };
  if (me.caps.performance) {
    call('GET /commercials/report/ads', { session, query: range });
    call('GET /commercials/report/surveys', { session, query: range });
    call('GET /commercials/report/games', { session, query: range });
  }
  if (me.caps.pageVisits) {
    call('GET /commercials/report/page-visits', { session, query: range });
  }
  think();
  if (me.caps.plan === 'PREMIUM') {
    call('GET /commercial/reports/executive', { session, query: { startDate: isoDate(-30), endDate: isoDate(0) }, reject: REJ });
  }
  call('GET /commercials/report/payout', { session, query: { year: YEAR, month: MONTH } });
  call('GET /commercials/report/payouts', { session, query: { year: YEAR } });
  call('GET /commercials/report/sales/anually', { session, query: { year: YEAR } });
  call('GET /commercials/report/sales/count', { session, query: { startDate: isoDate(-30), endDate: isoDate(0) } });
  call('GET /commercials/report/sales/daily', {
    session, query: { startDate: isoDate(-30), endDate: isoDate(0), page: 0, size: 20 },
  });
}

function wallet(session, me) {
  call('GET /commercial/wallet/me/billing-summary', { session });
  call('GET /commercial/wallet/me/deposits', { session, query: { year: YEAR, month: MONTH, page: 0, size: 10 } });
  call('GET /commercial/wallet/me/payouts', { session, query: { year: YEAR, month: MONTH, page: 0, size: 10 } });
  // Solo los comerciales STANDARD tienen cuenta de prosperidad.
  if (me.caps.plan === 'STANDARD') {
    call('GET /commercial/prosperity', { session, reject: REJ });
    call('GET /commercial/prosperity/movements', { session, query: { page: 0, size: 20 }, reject: REJ });
  }
  call('GET /commercial/allies', { session, reject: REJ });
  call('GET /commercial/allies/promoters', { session, reject: REJ });
  const promotions = call('GET /commercial/allies/promotions', { session, json: true, reject: REJ });
  if (often(0.05)) {
    // Activa y desactiva la promoción de un producto: queda como estaba (rechazo si el producto no es del aliado).
    const promo = pick(rows(promotions.body));
    const productId = promo && (promo.productId || promo.id) ? (promo.productId || promo.id) : 1;
    call('PATCH /commercial/allies/promotions/{productId}', { session, path: { productId }, ok: [200, 204], reject: REJ });
    call('PATCH /commercial/allies/promotions/{productId}', { session, path: { productId }, ok: [200, 204], reject: REJ });
  }
}

function sales(session) {
  call('GET /purchaseItems/totalSales', { session });
  call('GET /purchaseItems/topSelling', { session, query: { page: 0, size: 5 } });
  const pending = call('GET /purchaseItems/pending-claims', { session, query: { page: 0, size: 10 }, json: true });
  call('GET /productsReviews/commercial/avg', { session });
  // Reclamo físico con un PIN que no corresponde a nada: camino de rechazo (la validación real exige un PIN vivo).
  const item = pick(rows(pending.body));
  call('POST /purchaseItems/{purchaseItemId}/claim', {
    session, path: { purchaseItemId: item && item.id ? item.id : 1 }, body: { pin: '000000' }, ok: [200, 204], reject: REJ,
  });
}

// ---------------------------------------------------------------------------------------------
// Onboarding y contrato: los comerciales sembrados ya lo terminaron, así que las escrituras salen
// por el camino de rechazo (guarda de paso en el servicio), y las lecturas miden el camino feliz.
// ---------------------------------------------------------------------------------------------
function onboarding(session) {
  call('GET /commercials/onboarding/status', { session });
  call('GET /commercials/onboarding/summary', { session, reject: REJ });
  call('GET /commercials/onboarding/diagnostic/questionnaire', { session });
  call('GET /commercials/onboarding/classification', { session, reject: REJ });
  call('GET /commercials/onboarding/plan', { session, reject: REJ });
  call('GET /commercials/onboarding/documents', { session, reject: REJ });
  call('GET /commercials/onboarding/contract', { session, reject: REJ });
  think();
  if (!often(0.1)) return;
  call('POST /commercials/onboarding/terms', { session, body: { termsVersion: '1', accepted: true }, reject: REJ });
  call('POST /commercials/onboarding/legal-identification', {
    session,
    body: {
      personType: 'JURIDICA', companyName: 'Empresa ficticia', nit: '9000000001', legalRepFirstName: 'Representante',
      legalRepLastName: 'Prueba', legalRepDocType: 'CC', legalRepDocNumber: '1000000000', legalRepPepDeclaration: false,
      economicActivityDescription: 'Actividad ficticia de la prueba de carga', address: 'Dirección ficticia 1',
    },
    reject: REJ,
  });
  call('POST /commercials/onboarding/diagnostic', { session, body: {}, reject: REJ });
  call('POST /commercials/onboarding/classification/confirm', { session, reject: REJ });
  call('POST /commercials/onboarding/plan/accept', { session, body: { planCode: 'BASIC' }, reject: REJ });
  call('POST /commercials/onboarding/documents/prepare-upload', {
    session,
    body: { documentType: 'RUT', originalFileName: 'rut.pdf', contentType: 'application/pdf', sizeBytes: 1024 },
    reject: REJ,
  });
  call('POST /commercials/onboarding/documents/{documentId}/confirm', { session, path: { documentId: 1 }, reject: REJ });
  call('POST /commercials/onboarding/documents/{documentId}/discard', { session, path: { documentId: 1 }, reject: REJ });
  call('POST /commercials/onboarding/documents/continue', { session, reject: REJ });
  call('POST /commercials/onboarding/contract/generate', { session, reject: REJ });
  call('POST /commercials/onboarding/contract/approve', { session, reject: REJ });
  call('POST /commercials/onboarding/contract/request-changes', { session, reject: REJ });
  call('POST /commercials/onboarding/pay', { session, reject: REJ });
}

// ---------------------------------------------------------------------------------------------
// Planes: cambio de plan (deja un contrato en revisión de VERYGANA para cumplimiento), recarga y pago
// ---------------------------------------------------------------------------------------------
function plans(session, me) {
  const catalog = call('GET /plans/catalog', { session, json: true });
  const state = call('GET /plans/commercial/state', { session, json: true });
  const options = (catalog.body && catalog.body.plans) || [];
  const current = (state.body && state.body.effectivePlan) || (catalog.body && catalog.body.currentPlanCode);
  // Plan destino: otro de pago distinto del actual, con su inversión mínima (BASIC es una suscripción fija).
  const target = options.find((o) => o.planCode !== current && o.planCode !== 'BASIC' && o.minInvestmentCents);
  const targetCode = target ? target.planCode : (current === 'PREMIUM' ? 'STANDARD' : 'PREMIUM');
  const investment = (target && target.minInvestmentCents) || 100000000;

  call('GET /plans/change-request/preview', {
    session, query: { targetPlanCode: targetCode, intendedInvestmentAmountCents: investment }, reject: REJ,
  });
  const currentRequest = call('GET /plans/change-request/current', { session, json: true, ok: [200, 204], reject: REJ });
  // Referencia de la suscripción sembrada del comercial (LT-SUB-<n>); el servicio consulta el pago al simulador de Wompi.
  call('GET /plans/status/{reference}', { session, path: { reference: `LT-SUB-${me.number}` }, reject: REJ });
  think();

  if (!often(0.1)) return;
  // Cambio de plan: solicitud -> el comercial aprueba el contrato (queda para revisión de VERYGANA) -> o se cancela.
  const requested = call('POST /plans/change-request', {
    session, body: { targetPlanCode: targetCode, intendedInvestmentAmountCents: investment }, ok: [200, 201], json: true, reject: REJ,
  });
  const open = requested.body || (currentRequest.body && currentRequest.body.id ? currentRequest.body : null);
  const contractId = open && (open.contractId || (open.contract && open.contract.id) || (open.contract && open.contract.contractId));
  if (open && contractId) {
    call('POST /plans/change-request/contract/{contractId}/approve', {
      session, path: { contractId }, ok: [200, 204], reject: REJ,
    });
  }
  if (open && open.id) {
    if (SMOKE || open.requiredTopUpAmountCents > 0) {
      call('POST /plans/change-request/{id}/top-up-checkout', { session, path: { id: open.id }, reject: REJ_ILLEGAL_STATE });
    }
    // En smoke se deja la solicitud abierta para que cumplimiento la revise; en carga la mitad se cancela.
    if (!SMOKE && Math.random() < 0.5) {
      call('POST /plans/change-request/{id}/cancel', { session, path: { id: open.id }, ok: [200, 204], reject: REJ });
    }
    call('POST /plans/change-request/{id}/acknowledge-rejection', { session, path: { id: open.id }, ok: [200, 204], reject: REJ });
  } else if (SMOKE) {
    call('POST /plans/change-request/{id}/top-up-checkout', { session, path: { id: 1 }, reject: REJ_ILLEGAL_STATE });
    call('POST /plans/change-request/{id}/cancel', { session, path: { id: 1 }, reject: REJ });
    call('POST /plans/change-request/{id}/acknowledge-rejection', { session, path: { id: 1 }, reject: REJ });
    call('POST /plans/change-request/contract/{contractId}/approve', { session, path: { contractId: 1 }, reject: REJ });
  }

  // Recarga de presupuesto (STANDARD/PREMIUM): solicitud -> detalle -> checkout -> cancelar.
  // El monto mínimo de recarga depende del plan: sale de la vista previa (en pesos).
  let rechargeCents = 0;
  if (me.caps.recharge) {
    // Recarga en curso: 204 si no hay ninguna.
    call('GET /plans/recharge/current', { session, ok: [200, 204], reject: REJ });
    const preview = call('GET /plans/recharge/preview', { session, query: { amountCents: 5000000 }, json: true, reject: REJ });
    rechargeCents = ((preview.body && preview.body.minInvestmentPesos) || 1000000) * 100;
  }
  const recharge = me.caps.recharge
    ? call('POST /plans/recharge/request', { session, body: { amountCents: rechargeCents }, ok: [200, 201], json: true, reject: REJ_ILLEGAL_STATE })
    : { body: null };
  const rid = recharge.body && (recharge.body.contractId || recharge.body.id);
  if (rid) {
    call('GET /plans/recharge/{contractId}', { session, path: { contractId: rid }, reject: REJ });
    call('POST /plans/recharge/{contractId}/checkout', { session, path: { contractId: rid }, reject: REJ_ILLEGAL_STATE });
    // Conciliación con Wompi: 204 si la recarga ya no está en curso.
    call('POST /plans/recharge/{contractId}/reconcile', { session, path: { contractId: rid }, ok: [200, 204], reject: REJ_ILLEGAL_STATE });
    call('POST /plans/recharge/{contractId}/cancel', { session, path: { contractId: rid }, ok: [200, 204], reject: REJ });
  } else if (SMOKE) {
    call('GET /plans/recharge/{contractId}', { session, path: { contractId: 1 }, reject: REJ });
    call('POST /plans/recharge/{contractId}/checkout', { session, path: { contractId: 1 }, reject: REJ_ILLEGAL_STATE });
    call('POST /plans/recharge/{contractId}/reconcile', { session, path: { contractId: 1 }, ok: [200, 204], reject: REJ_ILLEGAL_STATE });
    call('POST /plans/recharge/{contractId}/cancel', { session, path: { contractId: 1 }, reject: REJ });
  }
  // Checkout de suscripción: con un plan activo no aplica (IllegalStateException sin mapear -> 500, solo tolerado en smoke).
  if (SMOKE) {
    call('POST /plans/checkout', { session, body: { planCode: 'BASIC' }, reject: REJ_ILLEGAL_STATE });
  }
}

// ---------------------------------------------------------------------------------------------
// Métodos de pago (OTP fijo del falso de SMS)
// ---------------------------------------------------------------------------------------------
function payoutMethods(session) {
  const banks = call('GET /commercial/payout-methods/banks', { session, json: true });
  call('GET /commercial/payout-methods', { session, query: { page: 0, size: 10 } });
  if (!often(0.1)) return;
  const phone = `399${String(Date.now()).slice(-7)}`;
  const created = call('POST /commercial/payout-methods', {
    session,
    body: {
      type: 'NEQUI', alias: `LT ${uniqueTag()}`.slice(0, 40), phoneNumber: phone, accountHolderName: 'Titular Ficticio',
      accountHolderDocType: 'CC', accountHolderDoc: '1000000000',
    },
    ok: [200, 201], json: true, reject: REJ,
  });
  const id = created.body && created.body.id;
  if (id) {
    call('POST /commercial/payout-methods/{id}/resend-otp', { session, path: { id }, ok: [200, 204], reject: REJ });
    call('POST /commercial/payout-methods/{id}/verify-otp', {
      session, path: { id }, body: { code: OTP_CODE }, ok: [200, 204], reject: REJ,
    });
    call('PUT /commercial/payout-methods/{id}/set-default', { session, path: { id }, ok: [200, 204], reject: REJ });
    call('PUT /commercial/payout-methods/{id}/deactivate', { session, path: { id }, ok: [200, 204], reject: REJ });
  }
  // Cuenta bancaria: el banco se valida contra el catálogo del simulador de payouts; el certificado va a MinIO.
  const bank = pick(banks.body && (banks.body.data || banks.body));
  const bankId = bank && (bank.id || bank.code || bank.bankId);
  const account = call('POST /commercial/payout-methods', {
    session,
    body: {
      type: 'BANK_ACCOUNT', alias: `LT banco ${uniqueTag()}`.slice(0, 40), bankCode: bankId || 'loadtest-bank',
      accountNumber: '000000000000', bankAccountType: 'SAVINGS', accountHolderName: 'Titular Ficticio',
      accountHolderDocType: 'CC', accountHolderDoc: '1000000000',
    },
    ok: [200, 201], json: true, reject: REJ,
  });
  const accountId = account.body && account.body.id;
  const cert = call('POST /commercial/payout-methods/{id}/certificate/prepare', {
    session, path: { id: accountId || 1 },
    body: { originalFileName: 'certificado.png', contentType: 'image/png', sizeBytes: PNG_BYTES.byteLength }, json: true, reject: REJ,
  });
  const upload = uploadPermission(cert.body);
  if (accountId && upload && upload.id) {
    const put = putToStorage(upload.url, PNG_BYTES, 'image/png');
    if (put.status === 200) {
      call('POST /commercial/payout-methods/{id}/certificate/confirm', {
        session, path: { id: accountId }, body: { certificateAssetId: upload.id }, ok: [200, 201, 204], reject: REJ,
      });
    }
  } else if (SMOKE) {
    call('POST /commercial/payout-methods/{id}/certificate/confirm', {
      session, path: { id: accountId || 1 }, body: { certificateAssetId: 1 }, ok: [200, 201, 204], reject: REJ,
    });
  }
  if (accountId) {
    call('PUT /commercial/payout-methods/{id}/deactivate', { session, path: { id: accountId }, ok: [200, 204], reject: REJ });
  }
}

// ---------------------------------------------------------------------------------------------
// Anuncios
// ---------------------------------------------------------------------------------------------
function ads(session) {
  const mine = call('GET /ads/my-ads/filter', { session, query: { page: 0, size: 10 }, json: true, reject: REJ });
  const ad = pick(rows(mine.body));
  if (ad) {
    const detail = call('GET /ads/{adId}/details', { session, path: { adId: ad.id }, json: true });
    call('GET /adLike/{adId}/likes', { session, path: { adId: ad.id }, query: { page: 0, size: 10 }, reject: REJ });
    const d = detail.body || ad;
    think();
    if (often(0.15) && d.title && d.categories) {
      // Solo se editan anuncios PENDING o PAUSED: pausa, edita con los mismos datos y reactiva (queda como estaba).
      call('POST /ads/{id}/pause', { session, path: { id: ad.id }, reject: REJ });
      call('PUT /ads/{id}', {
        session, path: { id: ad.id },
        body: {
          title: d.title, description: d.description, targetUrl: d.targetUrl || undefined,
          categoryIds: (d.categories || []).map((c) => c.id), minAge: d.minAge, maxAge: d.maxAge,
          targetGender: d.targetGender || 'ALL',
        },
        reject: REJ,
      });
      call('POST /ads/{id}/activate', { session, path: { id: ad.id }, reject: REJ });
    }
    if (often(0.05)) {
      call('POST /ads/{id}/increase-budget', {
        session, path: { id: ad.id }, body: { expectedMaxLikes: d.maxLikes || ad.maxLikes || 1, additionalLikes: 1 }, reject: REJ,
      });
    }
  } else if (SMOKE) {
    call('GET /ads/{adId}/details', { session, path: { adId: 1 }, reject: REJ });
    call('GET /adLike/{adId}/likes', { session, path: { adId: 1 }, reject: REJ });
  }

  if (!often(0.1)) return;
  // Alta de un anuncio: URL prefirmada -> PUT al MinIO simulado -> análisis -> creación (queda PENDING de aprobación).
  const prepared = call('POST /ads/assets/prepare-upload', {
    session,
    body: { originalFileName: 'anuncio.png', contentType: 'image/png', sizeBytes: PNG_BYTES.byteLength, imageDurationSeconds: 10 },
    json: true, reject: REJ,
  });
  const upload = uploadPermission(prepared.body);
  if (upload && upload.id) {
    const put = putToStorage(upload.url, PNG_BYTES, 'image/png');
    if (put.status === 200) {
      const analysis = call('POST /ads/assets/{assetId}/analyze', { session, path: { assetId: upload.id }, json: true, reject: REJ });
      if (analysis.ok) {
        const categories = call('GET /categories/all', { session, json: true });
        const category = pick(categories.body);
        const price = Math.max((analysis.body && analysis.body.minPricePerLike) || 1, 1);
        call('POST /ads', {
          session,
          body: {
            assetId: upload.id, title: `LT Anuncio ${uniqueTag()}`.slice(0, 100),
            description: 'Anuncio ficticio de la prueba de carga', pricePerLike: price, maxLikes: 10,
            maxLikesPerUserPerDay: 1, targetUrl: 'https://loadtest.invalid/anuncio', categoryIds: category ? [category.id] : [1],
            minAge: 18, maxAge: 60, targetGender: 'ALL',
          },
          ok: [200, 201], reject: REJ,
        });
      } else if (analysis.status !== 200) {
        // El análisis falla marcando el asset huérfano; nada más que limpiar.
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Campañas de juegos
// ---------------------------------------------------------------------------------------------
function campaigns(session) {
  const list = call('GET /campaigns', { session, json: true });
  const campaign = pick(rows(list.body));
  if (!campaign) {
    if (SMOKE) call('GET /campaigns/{campaignId}', { session, path: { campaignId: 1 }, reject: REJ });
    return;
  }
  const detail = call('GET /campaigns/{campaignId}', { session, path: { campaignId: campaign.id }, json: true });
  think();
  if (often(0.1)) {
    // Pausa y reactiva: la campaña queda como estaba.
    call('PATCH /campaigns/update-status/{campaignId}', {
      session, path: { campaignId: campaign.id }, body: { status: 'PAUSED' }, ok: [200, 204], reject: REJ,
    });
    call('PATCH /campaigns/update-status/{campaignId}', {
      session, path: { campaignId: campaign.id }, body: { status: 'ACTIVE' }, ok: [200, 204], reject: REJ,
    });
  }
  if (often(0.1)) {
    call('PUT /campaigns/{campaignId}', {
      session, path: { campaignId: campaign.id }, body: { maxSessionsPerUserPerDay: 5 }, ok: [200, 204], reject: REJ,
    });
  }
  if (often(0.05)) {
    const budget = (detail.body && detail.body.budgetCents) || campaign.budgetCents || 1;
    call('POST /campaigns/{campaignId}/increase-budget', {
      session, path: { campaignId: campaign.id }, body: { expectedBudgetCents: budget, additionalBudgetCents: 100000 }, reject: REJ,
    });
  }
}

// ---------------------------------------------------------------------------------------------
// Encuestas
// ---------------------------------------------------------------------------------------------
function surveys(session) {
  const cost = call('GET /surveys/cost-per-question', { session, json: true });
  const list = call('GET /surveys/commercial', { session, query: { page: 0, size: 20 }, json: true });
  const survey = pick(rows(list.body));
  if (survey) {
    call('GET /surveys/commercial/{surveyId}', { session, path: { surveyId: survey.id } });
    call('GET /surveys/{surveyId}/analytics', { session, path: { surveyId: survey.id }, reject: REJ });
    call('GET /surveys/{surveyId}/responses', { session, path: { surveyId: survey.id }, query: { page: 0, size: 20 }, reject: REJ });
    if (often(0.1)) {
      call('GET /surveys/{surveyId}/responses/export', { session, path: { surveyId: survey.id }, query: { format: 'csv' }, reject: REJ });
    }
    if (often(0.05)) {
      // Pausa y reactiva (el estado que sí permite el comercial); si no aplica, rechazo de negocio.
      call('PATCH /surveys/{surveyId}/commercial-status', { session, path: { surveyId: survey.id }, query: { status: 'PAUSED' }, ok: [200, 204], reject: REJ });
      call('PATCH /surveys/{surveyId}/commercial-status', { session, path: { surveyId: survey.id }, query: { status: 'ACTIVE' }, ok: [200, 204], reject: REJ });
    }
    if (often(0.05)) {
      call('POST /surveys/{surveyId}/increase-budget', {
        session, path: { surveyId: survey.id },
        body: { expectedMaxResponses: survey.maxResponses || 1, additionalResponses: 1 }, reject: REJ,
      });
    }
  } else if (SMOKE) {
    call('GET /surveys/commercial/{surveyId}', { session, path: { surveyId: 1 }, reject: REJ });
  }
  think();

  if (!often(0.1)) return;
  const categories = call('GET /categories/all', { session, json: true });
  const category = pick(categories.body);
  const created = call('POST /surveys', {
    session,
    body: {
      title: `LT Encuesta ${uniqueTag()}`.slice(0, 200), description: 'Encuesta ficticia de la prueba de carga',
      maxResponses: 5, pricePerQuestionCents: costOf(cost.body), categoryIds: category ? [category.id] : [1],
      minAge: 18, maxAge: 60, targetGender: 'ALL',
      questions: [
        { text: '¿Le gusta el producto?', type: 'YES_NO', required: true },
        { text: '¿Cuál prefiere?', type: 'SINGLE_CHOICE', required: true, options: ['Uno', 'Dos'] },
      ],
    },
    ok: [200, 201], json: true, reject: REJ,
  });
  const id = created.body && (created.body.id || created.body.surveyId);
  if (id) {
    call('PUT /surveys/{surveyId}', { session, path: { surveyId: id }, body: { description: 'Encuesta editada' }, reject: REJ });
    call('PATCH /surveys/{surveyId}/submit', { session, path: { surveyId: id }, ok: [200, 204], reject: REJ });
    // Publicar exige aprobación de un admin (excluido): rechazo de negocio esperado.
    call('PATCH /surveys/{surveyId}/publish', { session, path: { surveyId: id }, ok: [200, 204], reject: REJ });
  } else if (SMOKE) {
    call('PUT /surveys/{surveyId}', { session, path: { surveyId: 1 }, body: { description: 'Encuesta editada' }, reject: REJ });
    call('PATCH /surveys/{surveyId}/submit', { session, path: { surveyId: 1 }, ok: [200, 204], reject: REJ });
    call('PATCH /surveys/{surveyId}/publish', { session, path: { surveyId: 1 }, ok: [200, 204], reject: REJ });
  }
}

// ---------------------------------------------------------------------------------------------
// Productos y stock
// ---------------------------------------------------------------------------------------------
function products(session) {
  call('GET /products/total-products', { session, query: { status: 'ACTIVE' } });
  const mine = call('GET /products/my-products', { session, query: { page: 0 }, json: true });
  const product = pick(rows(mine.body));
  if (product) {
    const info = call('GET /products/edit/{productId}', { session, path: { productId: product.id }, json: true, reject: REJ });
    const stock = call('GET /products/{productId}/stock', {
      session, path: { productId: product.id }, query: { page: 0, size: 10 }, json: true, reject: REJ,
    });
    const item = pick(rows(stock.body));
    if (item) {
      // Descifra el código de stock con la llave de productos (camino feliz de la lectura de un código).
      call('GET /products/{productId}/stock/{stockId}/code', { session, path: { productId: product.id, stockId: item.id }, reject: REJ });
    }
    call('GET /products/{productId}/private-image', { session, path: { productId: product.id }, reject: REJ });
    think();
    const d = info.body;
    if (d && d.name && often(0.1)) {
      call('PATCH /products/{productId}', {
        session, path: { productId: product.id },
        body: {
          name: d.name, description: d.description, productCategoryId: d.productCategoryId || (d.productCategory && d.productCategory.id) || 1,
          price: d.price,
        },
        reject: REJ,
      });
    }
    if (often(0.05)) {
      call('PATCH /products/{productId}/gameReward', { session, path: { productId: product.id }, ok: [200, 204], reject: REJ });
      call('PATCH /products/{productId}/gameReward', { session, path: { productId: product.id }, ok: [200, 204], reject: REJ });
    }
  } else if (SMOKE) {
    call('GET /products/edit/{productId}', { session, path: { productId: 1 }, reject: REJ });
  }

  if (!often(0.1)) return;
  // Alta de un producto digital con stock: imagen prefirmada -> MinIO -> confirmar -> stock -> borrar.
  const categories = call('GET /productCategories', { session, json: true });
  const category = pick(categories.body);
  const prepared = call('POST /products/prepare', {
    session, body: { originalFileName: 'producto.png', contentType: 'image/png', sizeBytes: PNG_BYTES.byteLength }, json: true, reject: REJ,
  });
  const upload = uploadPermission(prepared.body);
  let productId = null;
  if (upload && upload.id) {
    putToStorage(upload.url, PNG_BYTES, 'image/png');
    const confirmed = call('POST /products/confirm', {
      session,
      body: {
        productAssetId: upload.id,
        productData: {
          name: `LT Producto ${uniqueTag()}`.slice(0, 150), description: 'Producto ficticio de la prueba de carga',
          productCategoryId: category ? category.id : 1, price: 20000, productType: 'DIGITAL',
          stockItems: [{ code: `LTNEW-${uniqueTag()}-1` }, { code: `LTNEW-${uniqueTag()}-2` }],
        },
      },
      ok: [200, 201], json: true, reject: REJ,
    });
    productId = confirmed.body && (confirmed.body.id || confirmed.body.productId);
  }
  if (productId) {
    call('POST /products/{productId}/stock/bulk', {
      session, path: { productId }, body: [{ code: `LTADD-${uniqueTag()}` }], ok: [200, 201, 204], reject: REJ,
    });
    const newStock = call('GET /products/{productId}/stock', { session, path: { productId }, query: { page: 0, size: 10 }, json: true, reject: REJ });
    const row = pick(rows(newStock.body));
    if (row) {
      call('DELETE /products/{productId}/stock/{stockId}', { session, path: { productId, stockId: row.id }, ok: [200, 204], reject: REJ });
    }
    const imagePrepared = call('POST /products/{productId}/image/prepare', {
      session, path: { productId }, body: { originalFileName: 'nueva.png', contentType: 'image/png', sizeBytes: PNG_BYTES.byteLength },
      json: true, reject: REJ,
    });
    const imageUpload = uploadPermission(imagePrepared.body);
    if (imageUpload && imageUpload.id) {
      putToStorage(imageUpload.url, PNG_BYTES, 'image/png');
      call('POST /products/{productId}/image/confirm', {
        session, path: { productId }, body: { newAssetId: imageUpload.id }, ok: [200, 204], reject: REJ,
      });
    }
    call('DELETE /products/{productId}', { session, path: { productId }, ok: [200, 204], reject: REJ });
  } else if (SMOKE) {
    call('POST /products/{productId}/stock/bulk', { session, path: { productId: 1 }, body: [{ code: 'LTADD-1' }], ok: [200, 201, 204], reject: REJ });
    call('DELETE /products/{productId}/stock/{stockId}', { session, path: { productId: 1, stockId: 1 }, ok: [200, 204], reject: REJ });
    call('POST /products/{productId}/image/prepare', {
      session, path: { productId: 1 }, body: { originalFileName: 'nueva.png', contentType: 'image/png', sizeBytes: PNG_BYTES.byteLength }, reject: REJ,
    });
    call('POST /products/{productId}/image/confirm', { session, path: { productId: 1 }, body: { newAssetId: 1 }, ok: [200, 204], reject: REJ });
    call('DELETE /products/{productId}', { session, path: { productId: 1 }, ok: [200, 204], reject: REJ });
  }
}

// ---------------------------------------------------------------------------------------------
// Brandeo de juegos (el comercial escribe el brief; los recursos corporativos quedan privados y el diseñador publica)
// ---------------------------------------------------------------------------------------------
function branding(session) {
  const goals = call('GET /branding-requests/campaign-goals', { session, json: true });
  const games = call('GET /branding-requests/games', { session, query: { page: 0, size: 20 }, json: true });
  const game = pick(rows(games.body));
  if (game) {
    call('GET /branding-requests/games/{gameId}/brief-schema', { session, path: { gameId: game.id }, reject: REJ });
  } else if (SMOKE) {
    call('GET /branding-requests/games/{gameId}/brief-schema', { session, path: { gameId: 1 }, reject: REJ });
  }
  const list = call('GET /branding-requests', { session, json: true });
  const request = pick(rows(list.body));
  if (request) {
    call('GET /branding-requests/{id}', { session, path: { id: request.id } });
    call('GET /branding-requests/{id}/comments', { session, path: { id: request.id } });
    call('GET /branding-requests/{id}/preview-url', { session, path: { id: request.id }, reject: REJ });
    if (often(0.2)) {
      call('POST /branding-requests/{id}/comments', {
        session, path: { id: request.id }, body: { content: 'Comentario del comercial (prueba de carga)' }, ok: [200, 201], reject: REJ,
      });
    }
    // Sobre las solicitudes sembradas (en diseño) estas acciones salen por el camino de rechazo: no hay diseño que aprobar.
    if (often(0.1)) {
      call('POST /branding-requests/{id}/approve-design', { session, path: { id: request.id }, ok: [200, 204], reject: REJ });
      call('POST /branding-requests/{id}/request-design-changes', { session, path: { id: request.id }, ok: [200, 204], reject: REJ });
    }
  }
  think();

  if (!often(0.1) || !game) {
    if (SMOKE && !game) {
      call('POST /branding-requests', {
        session, body: { gameId: 1, campaignGoal: 'BRAND_AWARENESS', brandName: 'LT', brandDescription: 'LT', budgetCents: 1000000 }, ok: [200, 201], reject: REJ,
      });
    }
    return;
  }
  const goal = pick(goals.body && (goals.body.data || goals.body));
  // Una solicitud nueva termina enviada a revisión (queda en la cola de los diseñadores) o cancelada mientras
  // es borrador (solo se cancela un borrador). En smoke se recorren las dos.
  createBrandingRequest(session, game, goal, SMOKE ? 'submit' : (Math.random() < 0.5 ? 'submit' : 'cancel'));
  if (SMOKE) {
    createBrandingRequest(session, game, goal, 'cancel');
  }
}

function createBrandingRequest(session, game, goal, ending) {
  const created = call('POST /branding-requests', {
    session,
    body: {
      gameId: game.id, campaignGoal: (goal && (goal.code || goal.value || goal.name || goal)) || 'BRAND_AWARENESS',
      brandName: `LT Marca ${uniqueTag()}`.slice(0, 200), brandDescription: 'Marca ficticia de la prueba de carga',
      targetUrl: 'https://loadtest.invalid/marca', budgetCents: 1000000,
    },
    ok: [200, 201], json: true, reject: REJ,
  });
  const id = created.body && created.body.id;
  if (!id) return;
  call('PATCH /branding-requests/{id}/brief', { session, path: { id }, body: { content: { slogan: 'Prueba de carga' } }, ok: [200, 204], reject: REJ });
  call('PATCH /branding-requests/{id}/config', {
    session, path: { id }, body: { minAge: 18, maxAge: 60, targetGender: 'ALL', maxSessionsPerUserPerDay: 3 }, ok: [200, 204], reject: REJ,
  });
  const resource = call('POST /branding-requests/{id}/corporate-resources/upload-url', {
    session, path: { id },
    body: { originalFileName: 'logo.png', contentType: 'image/png', sizeBytes: PNG_BYTES.byteLength }, json: true, reject: REJ,
  });
  const upload = uploadPermission(resource.body);
  if (upload && upload.id) {
    const put = putToStorage(upload.url, PNG_BYTES, 'image/png');
    if (put.status === 200) {
      call('POST /branding-requests/{id}/corporate-resources/confirm', {
        session, path: { id }, body: { resourceId: upload.id }, ok: [200, 204], reject: REJ,
      });
    }
  }
  call('POST /branding-requests/{id}/comments', {
    session, path: { id }, body: { content: 'Brief del comercial (prueba de carga)' }, ok: [200, 201], reject: REJ,
  });
  if (ending === 'submit') {
    call('POST /branding-requests/{id}/submit', { session, path: { id }, body: { notes: 'Prueba de carga' }, ok: [200, 204], reject: REJ });
  } else {
    call('POST /branding-requests/{id}/cancel', { session, path: { id }, ok: [200, 204], reject: REJ });
  }
}

// ---------------------------------------------------------------------------------------------
// Mascotas: integración de catálogo (solo PREMIUM; el resto responde 403)
// ---------------------------------------------------------------------------------------------
function petRequests(session) {
  const list = call('GET /commercial/pet/requests', { session, json: true, reject: REJ });
  call('GET /commercial/pet/requests/metrics', { session, query: { from: isoDate(-30), to: isoDate(0) }, reject: REJ });
  call('GET /commercial/pet/requests/metrics/daily', { session, query: { from: isoDate(-30), to: isoDate(0) }, reject: REJ });
  const request = pick(rows(list.body));
  const id = request ? request.id : 1;
  if (request || SMOKE) {
    call('GET /commercial/pet/requests/{id}/comments', { session, path: { id }, reject: REJ });
    if (often(0.2)) {
      call('POST /commercial/pet/requests/{id}/comments', {
        session, path: { id }, body: { content: 'Comentario del comercial (prueba de carga)' }, ok: [200, 201], reject: REJ,
      });
    }
  }
  if (!often(0.1)) return;
  const prepared = call('POST /commercial/pet/requests/image', {
    session, body: { contentType: 'image/png', originalFileName: 'producto.png', sizeBytes: PNG_BYTES.byteLength }, json: true, reject: REJ,
  });
  const upload = uploadPermission(prepared.body);
  if (upload && upload.objectKey) {
    const put = putToStorage(upload.url, PNG_BYTES, 'image/png');
    if (put.status === 200) {
      call('POST /commercial/pet/requests', {
        session,
        body: {
          productName: `LT Producto mascota ${uniqueTag()}`.slice(0, 100), description: 'Solicitud ficticia de la prueba de carga',
          imageObjectKey: upload.objectKey, desiredEffects: 'Suma hambre y da experiencia', budgetCents: 100000,
        },
        ok: [200, 201], reject: REJ,
      });
    }
  } else if (SMOKE) {
    call('POST /commercial/pet/requests', {
      session,
      body: { productName: 'LT', description: 'LT', imageObjectKey: 'loadtest/none.png', desiredEffects: 'LT', budgetCents: 100000 },
      ok: [200, 201], reject: REJ,
    });
  }
}

// ---------------------------------------------------------------------------------------------
// Registro de un comercial nuevo (bajo: crea una cuenta sin onboarding)
// ---------------------------------------------------------------------------------------------
function register() {
  call('POST /auth/register/commercial', { body: registerCommercialPayload(uniqueTag().replace('-', '')), ok: [200, 201], reject: REJ });
}

// ---------------------------------------------------------------------------------------------
// Sesiones
// ---------------------------------------------------------------------------------------------
// [peso, área, capacidad del plan que exige]
const WEIGHTED = [
  [20, reports, null], [15, wallet, null], [10, sales, null], [10, ads, 'advertise'], [10, products, 'products'],
  [8, surveys, 'surveys'], [7, campaigns, 'games'], [6, branding, 'games'], [5, plans, null],
  [4, payoutMethods, null], [3, onboarding, null], [2, petRequests, 'pets'],
];

function chooseArea(me) {
  const eligible = WEIGHTED.filter(([, , cap]) => cap === null || me.caps[cap]);
  const total = eligible.reduce((acc, [w]) => acc + w, 0);
  let roll = Math.random() * total;
  for (const [weight, area] of eligible) {
    roll -= weight;
    if (roll < 0) return area;
  }
  return reports;
}

export function commercialSession() {
  if (Math.random() < 0.02) {
    register();
    sleep(sessionGap());
    return;
  }
  const identifier = myIdentifier('commercial');
  const session = login(identifier);
  if (!session) {
    sleep(sessionGap());
    return;
  }
  const me = m0(session, identifier);
  account(session, me);
  think();
  chooseArea(me)(session, me);
  logout(session);
  sleep(sessionGap());
}

/** Cancela una solicitud de cambio de plan que haya quedado abierta de una corrida anterior (bloquea crear activos). */
function clearOpenPlanChange(session) {
  const current = call('GET /plans/change-request/current', { session, json: true, ok: [200, 204], reject: REJ });
  if (current.body && current.body.id) {
    call('POST /plans/change-request/{id}/cancel', { session, path: { id: current.body.id }, ok: [200, 204], reject: REJ });
  }
}

// Smoke: dos sesiones porque las capacidades dependen del plan. Un PREMIUM (n = 4) recorre anuncios, campañas,
// encuestas, brandeo, mascotas, planes y métodos de pago; un STANDARD (n = 2) recorre productos, prosperidad
// y recargas. Plans va al final de la primera sesión: el cambio de plan que deja pendiente lo toma cumplimiento
// (su smoke hace una segunda ronda tras una pausa) y mientras está abierto bloquea crear activos, por eso la
// primera sesión empieza cancelando el de la corrida anterior.
export function commercialSmoke() {
  const available = availableFor('commercial', SCENARIO);

  const premiumId = identifierFor('commercial', 3, available);
  const premium = login(premiumId);
  if (premium) {
    clearOpenPlanChange(premium);
    const me = m0(premium, premiumId);
    account(premium, me);
    [reports, wallet, sales, onboarding, payoutMethods, ads, campaigns, surveys, branding, petRequests, plans]
      .forEach((area) => area(premium, me));
    logout(premium);
  }

  const standardId = identifierFor('commercial', 1, available);
  const standard = login(standardId);
  if (standard) {
    const me = m0(standard, standardId);
    [reports, wallet, sales, products, plans].forEach((area) => area(standard, me));
    register();
    logout(standard);
  }
}
