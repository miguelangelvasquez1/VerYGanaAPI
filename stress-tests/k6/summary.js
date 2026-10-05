// Resumen de la corrida: una fila por endpoint ejercitado con p50/p95/p99 y tasa de error,
// los endpoints con recorrido que no se ejercitaron y las exclusiones con su motivo.
// Solo usa nombres de plantilla ("METHOD /ruta/{x}"): ni URLs con datos ni usuarios.
import { ENDPOINTS, METRIC_INDEX } from './lib/inventory.js';
import { SCENARIO } from './lib/config.js';

const RESULTS_DIR = __ENV.RESULTS_DIR || '/results';

function pad(text, width) {
  const s = String(text);
  return s.length >= width ? s : s + ' '.repeat(width - s.length);
}

function num(value, digits = 0) {
  return typeof value === 'number' && isFinite(value) ? value.toFixed(digits) : '-';
}

function valuesOf(data, metricName) {
  const m = data.metrics[metricName];
  return m && m.values ? m.values : {};
}

function rowFor(data, endpoint) {
  const index = METRIC_INDEX[endpoint.name];
  if (index === undefined) return null;
  const d = valuesOf(data, `ep_duration{ep:${index}}`);
  const f = valuesOf(data, `ep_failed{ep:${index}}`);
  const r = valuesOf(data, `ep_rejected{ep:${index}}`);
  const calls = d.count || 0;
  return {
    endpoint: endpoint.name,
    journey: endpoint.journey || `excl:${endpoint.exclusion}`,
    count: calls,
    p50: d.med,
    p95: d['p(95)'],
    p99: d['p(99)'],
    errorRate: calls > 0 ? (f.rate || 0) : null,
    rejected: r.count || 0,
  };
}

export function buildSummary(data) {
  const rows = [];
  const notExercised = [];
  const excluded = {};
  for (let i = 0; i < ENDPOINTS.length; i += 1) {
    const e = ENDPOINTS[i];
    if (e.exclusion !== '') {
      (excluded[e.exclusion] = excluded[e.exclusion] || []).push(e.name);
    }
    if (e.journey === '' && METRIC_INDEX[e.name] === undefined) continue;
    const row = rowFor(data, e);
    if (!row) continue;
    if (row.count > 0) {
      rows.push(row);
    } else if (e.journey !== '') {
      notExercised.push(e.name);
    }
  }
  rows.sort((a, b) => (a.journey + a.endpoint).localeCompare(b.journey + b.endpoint));

  const http = (n) => valuesOf(data, n);
  const duration = http('http_req_duration');
  const global = {
    scenario: SCENARIO,
    requests: http('http_reqs').count,
    reqPerSecond: http('http_reqs').rate,
    p50: duration.med,
    p95: duration['p(95)'],
    p99: duration['p(99)'],
    errorRate: http('http_req_failed').rate,
    iterations: http('iterations').count,
    droppedIterations: http('dropped_iterations').count || 0,
    unknownEndpointCalls: http('ep_unknown_duration').count || 0,
  };
  return { global, rows, notExercised, excluded };
}

export function renderText(summary) {
  const g = summary.global;
  const lines = [];
  lines.push(`== Resumen del escenario ${g.scenario} ==`);
  lines.push(
    `peticiones=${g.requests} req/s=${num(g.reqPerSecond, 1)} p50=${num(g.p50)}ms p95=${num(g.p95)}ms ` +
    `p99=${num(g.p99)}ms error=${num((g.errorRate || 0) * 100, 2)}% iteraciones=${g.iterations} ` +
    `descartadas=${g.droppedIterations}`
  );
  if (g.unknownEndpointCalls > 0) {
    lines.push(`AVISO: ${g.unknownEndpointCalls} llamadas a endpoints que no están en el inventario`);
  }
  lines.push('');
  lines.push(`== Endpoints ejercitados (${summary.rows.length}) ==`);
  lines.push(`${pad('recorrido', 14)}${pad('endpoint', 66)}${pad('n', 8)}${pad('p50', 8)}${pad('p95', 8)}${pad('p99', 8)}${pad('error%', 8)}rechazos`);
  summary.rows.forEach((r) => {
    lines.push(
      `${pad(r.journey, 14)}${pad(r.endpoint, 66)}${pad(r.count, 8)}${pad(num(r.p50), 8)}${pad(num(r.p95), 8)}` +
      `${pad(num(r.p99), 8)}${pad(num((r.errorRate || 0) * 100, 1), 8)}${r.rejected}`
    );
  });
  lines.push('');
  lines.push(`== Con recorrido y sin ejercitar (${summary.notExercised.length}) ==`);
  summary.notExercised.forEach((n) => lines.push(`  ${n}`));
  lines.push('');
  Object.keys(summary.excluded).sort().forEach((reason) => {
    lines.push(`== Excluidos por "${reason}" (${summary.excluded[reason].length}) ==`);
    summary.excluded[reason].forEach((n) => lines.push(`  ${n}`));
  });
  return lines.join('\n') + '\n';
}

export function handleSummary(data) {
  const summary = buildSummary(data);
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const out = { stdout: renderText(summary) };
  out[`${RESULTS_DIR}/summary-${SCENARIO}-${stamp}.json`] = JSON.stringify(summary, null, 2);
  return out;
}
