// Recorrido del oficial de cumplimiento (ROLE_COMPLIANCE_OFFICER).
// Colas en GET (contratos, cambios de plan, negociaciones, KYC, screenings, auditoría), consulta de
// antecedentes contra el simulador de ZapSign (WireMock: POST/GET de checks) y decisiones sobre lo
// que el comercial dejó pendiente (cambio de plan -> PENDING_VERYGANA_REVIEW).
//
// ZapSign realmente ejercitado: crear/refrescar/consultar antecedentes (BackgroundCheckServiceImpl)
// y, al aprobar un contrato pendiente, solicitar la firma electrónica (ESignatureServiceImpl).
// Los contratos sembrados ya están APPROVED: aprobar, rechazar o marcar firmado sobre ellos es un
// rechazo de negocio esperado (400/409), no cambia nada. Los 2 oficiales son personal interno fijo:
// un VU por usuario, que repite la ronda hasta el final (lib/util.js staffLoop). Los correos que
// devuelven las colas nunca se imprimen.
import { sleep } from 'k6';
import { call } from '../lib/http.js';
import {
  BUSINESS, SMOKE, claimsOf, often, pick, rows, staffLoop, think,
} from '../lib/util.js';

const NIL_UUID = '00000000-0000-0000-0000-000000000000';

function auditTrail(session) {
  call('GET /compliance/audit-logs', { session, query: { level: 'INFO', size: 20 } });
  call('GET /compliance/audit-logs/critical', { session, query: { size: 20 } });
}

function queues(session) {
  const kyc = call('GET /compliance/kyc/pending', { session, json: true });
  const hits = call('GET /compliance/screenings/hits', { session, query: { page: 0, size: 20 }, json: true });
  call('GET /compliance/plan-changes', { session });
  call('GET /compliance/contracts/negotiations', { session });
  // Historial de screenings del primer pendiente de KYC; si no hay, el de la propia cuenta (vacío, 200).
  const pending = pick(kyc.body);
  const subject = pending && (pending.publicId || pending.userPublicId)
    ? (pending.publicId || pending.userPublicId) : claimsOf(session).publicId || NIL_UUID;
  call('GET /compliance/screenings/user/{publicId}', { session, path: { publicId: subject } });
  return { kyc: pick(kyc.body), hit: pick(rows(hits.body)) };
}

/** Consulta de antecedentes de un contrato: pasa por el simulador de ZapSign. */
function backgroundChecks(session, contractId) {
  if (often(0.3)) {
    call('POST /compliance/contracts/{contractId}/background-checks', {
      session, path: { contractId }, reject: BUSINESS,
    });
  }
  const checks = call('GET /compliance/contracts/{contractId}/background-checks', {
    session, path: { contractId }, json: true,
  });
  const check = pick(checks.body);
  if (check && check.id) {
    call('POST /compliance/contracts/background-checks/{id}/refresh', { session, path: { id: check.id }, reject: BUSINESS });
    call('GET /compliance/contracts/background-checks/{id}/detail', { session, path: { id: check.id }, reject: BUSINESS });
  } else if (SMOKE) {
    call('POST /compliance/contracts/background-checks/{id}/refresh', { session, path: { id: 1 }, reject: BUSINESS });
    call('GET /compliance/contracts/background-checks/{id}/detail', { session, path: { id: 1 }, reject: BUSINESS });
  }
}

function contractsReview(session) {
  const pending = call('GET /compliance/contracts', {
    session, query: { status: 'PENDING_VERYGANA_REVIEW' }, json: true,
  });
  const all = call('GET /compliance/contracts', { session, json: true });
  const awaiting = pick(pending.body);
  const contract = awaiting || pick(all.body);
  if (!contract) {
    return null;
  }
  call('GET /compliance/contracts/{contractId}', { session, path: { contractId: contract.contractId } });
  backgroundChecks(session, contract.contractId);
  return { contract, awaiting: awaiting || null };
}

/**
 * Decisiones. Un contrato pendiente de VERYGANA se aprueba (pide la firma a ZapSign) o se rechaza;
 * sin pendientes, las mismas acciones sobre un contrato ya aprobado salen por el camino de rechazo.
 */
function decisions(session, found, queued) {
  const target = found ? found.contract : null;
  if (target && often(found.awaiting ? 0.7 : 0.1)) {
    const id = target.contractId;
    if (found.awaiting && (SMOKE || Math.random() < 0.7)) {
      call('POST /compliance/contracts/{contractId}/approve', {
        session, path: { contractId: id }, query: { commercialActivityType: 'PRODUCTS' }, reject: BUSINESS,
      });
      // El simulador no firma. En carga la mitad se marca firmada como lo haría el webhook (aplica el cambio de plan);
      // en smoke se deja en PENDING_SIGNATURE para no cambiar el plan de los comerciales de prueba (el comercial la
      // cancela al inicio de su siguiente smoke).
      if (!SMOKE && Math.random() < 0.5) {
        call('POST /compliance/contracts/{contractId}/esignature/mark-signed', { session, path: { contractId: id }, reject: BUSINESS });
      }
    } else {
      call('POST /compliance/contracts/{contractId}/reject', {
        session, path: { contractId: id }, body: { reason: 'Rechazo de la prueba de carga', documentsIssue: false }, reject: BUSINESS,
      });
      call('POST /compliance/contracts/{contractId}/approve', { session, path: { contractId: id }, reject: BUSINESS });
      // Marcar firmado un contrato que sí espera firma lo aplicaría: solo se prueba sobre los que no la esperan.
      if (target.status !== 'PENDING_SIGNATURE') {
        call('POST /compliance/contracts/{contractId}/esignature/mark-signed', { session, path: { contractId: id }, reject: BUSINESS });
      }
    }
  } else if (SMOKE && target) {
    call('POST /compliance/contracts/{contractId}/approve', { session, path: { contractId: target.contractId }, reject: BUSINESS });
    call('POST /compliance/contracts/{contractId}/reject', {
      session, path: { contractId: target.contractId },
      body: { reason: 'Rechazo de la prueba de carga', documentsIssue: false }, reject: BUSINESS,
    });
    if (target.status !== 'PENDING_SIGNATURE') {
      call('POST /compliance/contracts/{contractId}/esignature/mark-signed', {
        session, path: { contractId: target.contractId }, reject: BUSINESS,
      });
    }
  }

  if (often(0.2)) {
    // Sin KYC, negociaciones ni hits pendientes sembrados estas acciones salen por el camino de rechazo.
    const kyc = queued.kyc;
    const kycId = kyc && (kyc.publicId || kyc.userPublicId) ? (kyc.publicId || kyc.userPublicId) : NIL_UUID;
    call('POST /compliance/kyc/{publicId}/approve', { session, path: { publicId: kycId }, reject: BUSINESS });
    call('POST /compliance/kyc/{publicId}/reject', {
      session, path: { publicId: NIL_UUID }, query: { reason: 'Rechazo de la prueba de carga' }, reject: BUSINESS,
    });
    call('POST /compliance/contracts/negotiations/{onboardingId}/resolve', {
      session, path: { onboardingId: 1 }, reject: BUSINESS,
    });
    call('POST /compliance/screenings/{id}/review', {
      session, path: { id: queued.hit && queued.hit.id ? queued.hit.id : 1 }, query: { notes: 'Revisión de la prueba de carga' },
      reject: BUSINESS,
    });
  }
}

function round(session) {
  const queued = queues(session);
  think();
  const found = contractsReview(session);
  think();
  decisions(session, found, queued);
  auditTrail(session);
}

export function complianceLoop() {
  staffLoop('compliance', round);
}

// En smoke el comercial deja un cambio de plan pendiente de revisión mientras corre su recorrido:
// el oficial hace una primera ronda y otra después de una pausa para tomarlo.
export function complianceSmoke() {
  staffLoop('compliance', (session) => {
    round(session);
    sleep(30);
    round(session);
  });
}
