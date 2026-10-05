// Recorrido del diseñador D. Absorbe el ciclo de solicitud y las escrituras
// de stress-test-pets.js: catálogo, escenas y avisos de mascotas, más el brandeo de juegos (el diseñador
// es el auditor que publica los assets del anunciante, no al revés).
//
// Los 5 diseñadores son personal interno fijo: un VU por usuario que inicia sesión una vez y repite la
// ronda hasta el final de la prueba (lib/util.js staffLoop). Lo que crea lo borra en la misma ronda
// (catálogo, escena y aviso; el borrado de catálogo y escena es lógico). Las subidas van por URL
// prefirmada al MinIO simulado y se confirman contra la API.
import { call } from '../lib/http.js';
import {
  BUSINESS, PNG_BYTES, SMOKE, often, pick, putToStorage, rows, staffLoop, think, uniqueTag, uploadPermission,
} from '../lib/util.js';
import exec from 'k6/execution';

function profile(session) {
  call('GET /game-designers/me', { session });
  if (often(0.2)) {
    call('PATCH /game-designers/me', {
      session, body: { bio: 'Diseñador de la prueba de carga' }, ok: [200, 204], reject: BUSINESS,
    });
  }
}

// ---------------------------------------------------------------------------------------------
// Brandeo de juegos
// ---------------------------------------------------------------------------------------------
function branding(session) {
  const list = call('GET /game-designers/me/branding-requests', { session, json: true });
  const request = pick(rows(list.body));
  if (!request) {
    if (SMOKE) {
      call('GET /game-designers/me/branding-requests/{id}', { session, path: { id: 1 }, reject: BUSINESS });
    }
    return;
  }
  const id = request.id;
  call('GET /game-designers/me/branding-requests/{id}', { session, path: { id } });
  call('GET /game-designers/me/branding-requests/{id}/comments', { session, path: { id } });
  think();
  call('POST /game-designers/me/branding-requests/{id}/comments', {
    session, path: { id }, body: { content: 'Avance del diseño (prueba de carga)' }, ok: [200, 201], reject: BUSINESS,
  });
  // El borrador del formulario es un mapa libre: se sobrescribe completo (idempotente).
  call('PATCH /game-designers/me/branding-requests/{id}/draft', {
    session, path: { id }, body: { loadtest: true, note: `borrador ${uniqueTag()}` }, ok: [200, 204], reject: BUSINESS,
  });
  // Sin build de preview subido, la URL responde con rechazo de negocio.
  call('GET /game-designers/me/branding-requests/{id}/preview-url', { session, path: { id }, reject: BUSINESS });

  if (often(0.3)) {
    // Subida de un asset del diseño: URL prefirmada -> PUT al MinIO simulado -> confirmar -> borrar.
    const prepared = call('POST /game-designers/me/assets/upload-url', {
      session,
      body: {
        originalFileName: 'diseno.png', contentType: 'image/png', sizeBytes: PNG_BYTES.byteLength,
        brandingRequestId: id,
      },
      json: true,
      reject: BUSINESS,
    });
    const upload = uploadPermission(prepared.body);
    if (upload && upload.id) {
      const put = putToStorage(upload.url, PNG_BYTES, 'image/png');
      if (put.status === 200) {
        const confirmed = call('POST /game-designers/me/assets/confirm', {
          session, body: { assetId: upload.id }, ok: [200, 204], reject: BUSINESS,
        });
        if (confirmed.ok || confirmed.rejected) {
          call('DELETE /game-designers/me/assets/{assetId}', {
            session, path: { assetId: upload.id }, ok: [200, 204], reject: BUSINESS,
          });
        }
      }
    } else if (SMOKE) {
      call('POST /game-designers/me/assets/confirm', { session, body: { assetId: 1 }, ok: [200, 204], reject: BUSINESS });
      call('DELETE /game-designers/me/assets/{assetId}', { session, path: { assetId: 1 }, ok: [200, 204], reject: BUSINESS });
    }
  }
  // Entregar el diseño cambia el estado de la solicitud y exige un diseño completo: poco frecuente.
  if (often(0.05)) {
    call('POST /game-designers/me/branding-requests/{id}/submit-design', {
      session, path: { id }, ok: [200, 204], reject: BUSINESS,
    });
  }
}

// ---------------------------------------------------------------------------------------------
// Mascotas: catálogo, escenas, avisos y solicitudes de integración
// ---------------------------------------------------------------------------------------------
function petAssets(session) {
  const prepared = call('POST /game-designer/pet/assets', {
    session,
    body: {
      kind: 'SCENE_OBJECT', contentType: 'image/png', originalFileName: `lt-${uniqueTag()}.png`,
      sizeBytes: PNG_BYTES.byteLength,
    },
    json: true,
    reject: BUSINESS,
  });
  const upload = prepared.body;
  if (upload && upload.uploadUrl) {
    putToStorage(upload.uploadUrl, PNG_BYTES, 'image/png');
    return upload.objectKey || null;
  }
  return null;
}

function petCatalog(session, spriteObjectKey) {
  call('GET /game-designer/pet/catalog', { session });
  const item = {
    name: `LT Item ${uniqueTag()}`, description: 'Ítem de la prueba de carga', isMedicine: false, isDrink: false,
    curesAllParts: false, price: 25, spriteObjectKey, expWhenEating: 5, healthDelta: 0, energyDelta: 0,
    hungerDelta: 10, thirstDelta: 0, hygieneDelta: 0, humorDelta: 0, bodyFatDelta: 0, active: true,
  };
  const created = call('POST /game-designer/pet/catalog', {
    session, body: item, ok: [200, 201], json: true, reject: BUSINESS,
  });
  const id = created.body && created.body.id;
  if (id) {
    call('PUT /game-designer/pet/catalog/{id}', {
      session, path: { id }, body: Object.assign({}, item, { price: 30 }), reject: BUSINESS,
    });
    call('DELETE /game-designer/pet/catalog/{id}', { session, path: { id }, ok: [200, 204], reject: BUSINESS });
  }
}

function petScenes(session) {
  call('GET /game-designer/pet/scenes', { session });
  call('GET /game-designer/pet/scenes/canvas', { session });
  // sceneId 9000+ para no pisar los del juego (-1, 0, 1, 2); el DELETE es lógico.
  const scene = (suffix) => ({
    sceneId: 9000 + (exec.vu.idInInstance % 900),
    active: true,
    objects: [{
      objectId: `lt-${uniqueTag()}-${suffix}`, type: 'image', objectKey: 'scene-objects/loadtest/placeholder.png',
      x: 100, y: 200, width: 128, height: 128, scaleMultiplier: 1.0,
    }],
  });
  const created = call('POST /game-designer/pet/scenes', {
    session, body: scene('a'), ok: [200, 201], json: true, reject: BUSINESS,
  });
  const id = created.body && created.body.id;
  if (id) {
    call('PUT /game-designer/pet/scenes/{id}', { session, path: { id }, body: scene('b'), reject: BUSINESS });
    call('DELETE /game-designer/pet/scenes/{id}', { session, path: { id }, ok: [200, 204], reject: BUSINESS });
  }
}

function petNotifications(session) {
  call('GET /game-designer/pet/notifications', { session });
  const notification = (title) => ({
    externalId: `lt-note-${uniqueTag()}`, title, message: 'Aviso de la prueba de carga', imageUrl: null,
    buttonLabel: null, buttonUrl: null, date: new Date().toISOString().slice(0, 10), active: false,
  });
  // El aviso no se puede borrar con el id que devuelve la API (ver abajo): se crea inactivo para que no llegue a
  // los consumidores, y solo en una de cada diez rondas para que la tabla no crezca sin control.
  if (often(0.1)) {
    call('POST /game-designer/pet/notifications', { session, body: notification('Aviso LT'), ok: [200, 201], reject: BUSINESS });
  }
  // El DTO de respuesta expone el externalId (texto) como `id`, pero PUT y DELETE reciben la PK numérica: el
  // diseñador no tiene cómo conocerla (hallazgo conocido, no se arregla aquí). Se llaman con una PK que no existe
  // (camino de rechazo) para medir el endpoint; con el id de texto la API responde 500 por el tipo del parámetro.
  const missing = 900000000 + exec.vu.idInInstance;
  call('PUT /game-designer/pet/notifications/{id}', {
    session, path: { id: missing }, body: notification('Aviso LT editado'), reject: BUSINESS,
  });
  call('DELETE /game-designer/pet/notifications/{id}', { session, path: { id: missing }, ok: [200, 204], reject: BUSINESS });
}

function petRequests(session) {
  const list = call('GET /game-designer/pet/requests', { session, json: true });
  const request = pick(rows(list.body));
  // Sin solicitudes asignadas (las sembradas no lo están) el detalle sale por el camino de rechazo.
  const id = request ? request.id : 1;
  if (request || SMOKE) {
    call('GET /game-designer/pet/requests/{id}', { session, path: { id }, reject: BUSINESS });
    call('GET /game-designer/pet/requests/{id}/comments', { session, path: { id }, reject: BUSINESS });
    call('POST /game-designer/pet/requests/{id}/comments', {
      session, path: { id }, body: { content: 'Comentario de la prueba de carga' }, ok: [200, 201], reject: BUSINESS,
    });
    call('PATCH /game-designer/pet/requests/{id}/draft', {
      session, path: { id }, body: { name: 'Borrador LT', price: 25, description: 'Borrador de la prueba de carga', hungerDelta: 10 },
      ok: [200, 204], reject: BUSINESS,
    });
    if (often(0.05)) {
      call('POST /game-designer/pet/requests/{id}/publish', { session, path: { id }, ok: [200, 201, 204], reject: BUSINESS });
    }
  }
}

function round(session) {
  profile(session);
  const areas = [branding, branding, petRequests, (s) => {
    const key = petAssets(s);
    petCatalog(s, key);
  }, petScenes, petNotifications];
  if (SMOKE) {
    branding(session);
    petRequests(session);
    petCatalog(session, petAssets(session));
    petScenes(session);
    petNotifications(session);
    return;
  }
  pick(areas)(session);
}

export function designerLoop() {
  staffLoop('designer', round);
}

export function designerSmoke() {
  staffLoop('designer', round);
}
