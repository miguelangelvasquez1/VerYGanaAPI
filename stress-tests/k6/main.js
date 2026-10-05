// Punto de entrada de la prueba de carga.
//   k6 run -e SCENARIO=smoke|A|B|B_CEILING /scripts/main.js
// Un escenario k6 por rol: consumidores y comerciales con VUs variables, personal interno
// con VUs fijos (5/3/2). Los recorridos viven en journeys/.
import { SCENARIO, buildOptions } from './lib/config.js';
import { consumerSession, consumerSmoke } from './journeys/consumer.js';
import { commercialSession, commercialSmoke } from './journeys/commercial.js';
import { designerLoop, designerSmoke } from './journeys/designer.js';
import { adminLoop, adminSmoke } from './journeys/admin.js';
import { complianceLoop, complianceSmoke } from './journeys/compliance.js';
import { handleSummary } from './summary.js';

export const options = buildOptions(SCENARIO);

export {
  consumerSession, consumerSmoke,
  commercialSession, commercialSmoke,
  designerLoop, designerSmoke,
  adminLoop, adminSmoke,
  complianceLoop, complianceSmoke,
  handleSummary,
};

