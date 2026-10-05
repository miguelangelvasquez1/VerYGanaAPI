// Inventario de endpoints (stress-tests/inventory/endpoints.csv) leído al iniciar k6 y las métricas
// por endpoint que usa summary.js. Una sola fuente: el mismo CSV que valida
// LoadTestEndpointInventoryTest.
//
// El nombre de cada endpoint es "METHOD /plantilla/{ruta}" tal cual está en el controller: nunca
// la URL con datos.
import { SharedArray } from 'k6/data';
import { Counter, Rate, Trend } from 'k6/metrics';

const INVENTORY_FILE = __ENV.INVENTORY_FILE || '/inventory/endpoints.csv';

function parse(text) {
  const lines = text.split('\n').filter((l) => l.trim() !== '');
  return lines.slice(1).map((line) => {
    const [method, path, journey, exclusion] = line.trim().split(',');
    return { name: `${method} ${path}`, method, path, journey: journey || '', exclusion: exclusion || '' };
  });
}

// SharedArray: las filas se parsean una vez y los VUs las comparten (no se copian por VU).
export const ENDPOINTS = new SharedArray('inventory', () => parse(open(INVENTORY_FILE)));

// Webhooks: el recorrido de compra o de contrato los dispara aunque estén excluidos del inventario.
const ALSO_MEASURED = new Set(['POST /wompi/events', 'POST /zapsign/events']);

// Métricas por endpoint. Son TRES métricas globales con la etiqueta `ep` (índice de la fila del
// inventario), no 3 por endpoint: cada objeto Trend/Rate/Counter cuesta ~3 KB por VU y 1.071 de ellos
// eran ~3,2 MB por VU (≈ 6,5 GB con 2.000 VUs). El resumen lee cada endpoint como submétrica
// `ep_duration{ep:N}` (endpointThresholds() las declara para que k6 las materialice).
const EP_DURATION = new Trend('ep_duration', true);
const EP_FAILED = new Rate('ep_failed');
const EP_REJECTED = new Counter('ep_rejected');

/**
 * Endpoints medidos: nombre -> índice de la fila del inventario. Solo los que algún recorrido llama.
 * Es un único objeto con números (no un objeto por endpoint): cada objeto extra cuesta memoria por VU.
 */
export const METRIC_INDEX = {};
for (let i = 0; i < ENDPOINTS.length; i += 1) {
  const e = ENDPOINTS[i];
  if (e.journey !== '' || ALSO_MEASURED.has(e.name)) {
    METRIC_INDEX[e.name] = i;
  }
}

/** Registra una llamada de un endpoint (o de uno fuera del inventario si `name` no está medido). */
export function recordCall(name, durationMs, failed, rejected) {
  const index = METRIC_INDEX[name];
  if (index === undefined) {
    UNKNOWN.duration.add(durationMs);
    UNKNOWN.failed.add(failed ? 1 : 0);
    if (rejected) UNKNOWN.rejected.add(1);
    return;
  }
  const tags = { ep: String(index) };
  EP_DURATION.add(durationMs, tags);
  EP_FAILED.add(failed ? 1 : 0, tags);
  if (rejected) EP_REJECTED.add(1, tags);
}

/** Umbrales sin efecto (siempre pasan) que obligan a k6 a calcular la submétrica de cada endpoint. */
export function endpointThresholds() {
  const t = {};
  Object.keys(METRIC_INDEX).forEach((name) => {
    const tag = `{ep:${METRIC_INDEX[name]}}`;
    t[`ep_duration${tag}`] = ['max>=0'];
    t[`ep_failed${tag}`] = ['rate>=0'];
    t[`ep_rejected${tag}`] = ['count>=0'];
  });
  return t;
}

// Para llamadas a algo que no está en el inventario (se avisa en el resumen).
export const UNKNOWN = {
  duration: new Trend('ep_unknown_duration', true),
  failed: new Rate('ep_unknown_failed'),
  rejected: new Counter('ep_unknown_rejected'),
};
